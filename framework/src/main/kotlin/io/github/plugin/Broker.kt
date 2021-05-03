package io.github.plugin

import io.github.plugin.GrpcBroker.ConnInfo
import io.grpc.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

class Broker
internal constructor(
    private val scope: CoroutineScope,
    @PublishedApi internal val channelProvider: ChannelProvider,
    channel: ManagedChannel,
    private val dialTimeoutMillis: Long = 5000L,
) {
  private val nextId = AtomicInteger(0)
  private val stub = GRPCBrokerGrpcKt.GRPCBrokerCoroutineStub(channel)

  private val sender = Channel<ConnInfo>()
  private val logger = LoggerFactory.getLogger(javaClass)

  // mutex protects streams.
  private val mutex = Mutex()
  private val streams = mutableMapOf<Int, CompletableDeferred<ConnInfo>>()

  fun getNextId() = nextId.addAndGet(1)

  fun start() {
    scope.launch {
      stub
          .startStream(sender.consumeAsFlow())
          .catch { e ->
            when (e) {
              is StatusException -> sender.cancel(BrokerServiceException(e))
              else -> sender.cancel(CancellationException(e))
            }
          }
          .collect { connection ->
            logger.debug("Received connection info for service #${connection.serviceId}")
            mutex.withLock {
              val deferred = streams[connection.serviceId]
              if (deferred != null) {
                deferred.complete(connection)
              } else {
                streams[connection.serviceId] = CompletableDeferred(connection)
              }
            }
          }
    }
  }

  fun dial(serviceId: Int): ManagedChannel =
      runBlocking(scope.coroutineContext) {
        logger.debug("Dialing service #$serviceId")
        val deferred =
            mutex.withLock {
              streams[serviceId] ?: CompletableDeferred<ConnInfo>().also { streams[serviceId] = it }
            }

        val connection = withTimeout(dialTimeoutMillis) { deferred.await() }
        logger.debug("Received connection info for service #${serviceId}")

        channelProvider.clientChannel(connection.network, connection.address)
      }

  fun acceptAndServe(serviceId: Int, vararg services: BindableService): Server =
      runBlocking(scope.coroutineContext) {
        val server = channelProvider.server("unix", *services).start()
        logger.debug("Service #$serviceId has started at ${server.listenSocket.path()}")

        send(serviceId, "unix", server.listenSocket.path())

        server
      }

  private suspend fun send(serviceId: Int, network: String, address: String) {
    val connection =
        ConnInfo.newBuilder()
            .setServiceId(serviceId)
            .setNetwork(network)
            .setAddress(address)
            .build()
    sender.send(connection)
  }
}
