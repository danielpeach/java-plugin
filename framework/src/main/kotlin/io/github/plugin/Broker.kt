package io.github.plugin

import io.github.plugin.GrpcBroker.ConnInfo
import io.grpc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * [Broker] facilitates bi-directional communication between the plugin client and server.
 * */
class Broker internal constructor(
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

  /**
   * When a client-side plugin expects a callback from a server-side plugin implementation, it must
   * first acquire a service ID. The service ID is a correlation ID for network coordinates that
   * [Broker] will send out-of-band to the server once [acceptAndServe] has been called.
   *
   * The client must send the service ID as part of its initial gRPC message to the server.
   *
   * @returns [Int] the next service ID.
   * */
  fun getNextId() = nextId.addAndGet(1)

  /**
   * Clients should call [acceptAndServe] after acquiring a service ID but before sending the service ID
   * to the server.
   *
   * The client is responsible for cleaning up the returned [Server] after the client-server call sequence
   * has completed.
   *
   * The flow for a plugin implementor typically looks like this:
   *
   * - Client acquires a service ID via [getNextId].
   *
   * - Client serves one or more gRPC services via [acceptAndServe].
   *
   * - Client sends message that includes its service ID ("please dial me back at this number").
   *
   * - Server (the server-side plugin implementation) receives message with service ID.
   *   The Go plugin framework has a method identical to [dial] below that accepts a service ID and
   *   facilitates communication back to the client. The server-side plugin should handle this callback synchronously.
   *
   * - Client cleans up its [Server].
   *
   * @param [serviceId] the acquired service ID.
   *
   * @param [services] gRPC [BindableService]s that [Broker] should serve.
   *
   * @returns [Server] a gRPC server that implements the provided [services].
   * */
  fun acceptAndServe(serviceId: Int, vararg services: BindableService): Server = runBlocking(scope.coroutineContext) {
    val server = channelProvider.server("unix", *services).start()
    logger.debug("Service #$serviceId has started at ${server.listenSocket.path()}")

    send(serviceId, "unix", server.listenSocket.path())

    server
  }

  /**
   * Method [dial] is the complement of [acceptAndServe]. It should be used when a server has passed the client a service ID
   * that should be used to call back the server. This should be rare, since this only arises if the client is calling
   * back the server after the server called back the client.
   *
   * @param [serviceId] a service ID provided by the plugin server.
   *
   * @returns [ManagedChannel] a channel that should be used by a gRPC client stub to communicate with the server.
   * */
  fun dial(serviceId: Int): ManagedChannel = runBlocking(scope.coroutineContext) {
    logger.debug("Dialing service #$serviceId")
    val deferred = mutex.withLock {
      streams[serviceId] ?: CompletableDeferred<ConnInfo>().also { streams[serviceId] = it }
    }

    val connection = withTimeout(dialTimeoutMillis) { deferred.await() }
    logger.debug("Received connection info for service #$serviceId")

    channelProvider.clientChannel(connection.network, connection.address)
  }

  internal fun start() {
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

  private suspend fun send(serviceId: Int, network: String, address: String) {
    val connection = ConnInfo.newBuilder()
      .setServiceId(serviceId)
      .setNetwork(network)
      .setAddress(address)
      .build()
    sender.send(connection)
  }
}
