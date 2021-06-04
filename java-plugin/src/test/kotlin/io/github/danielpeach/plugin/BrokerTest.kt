package io.github.danielpeach.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.github.danielpeach.plugin.grpc.GRPCBrokerGrpcKt
import io.github.danielpeach.plugin.grpc.GrpcBroker.ConnInfo
import io.github.danielpeach.plugin.internal.TestClient
import io.github.danielpeach.plugin.internal.TestService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.future
import receiveBlockingWithTimeout
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo

class BrokerTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      brokerServer.shutdownAndAwait()
      brokerChannel.shutdownAndAwait()
    }

    test("acceptAndServe starts server, sends connection info to broker server") {
      // Client
      brokerClient.start()
      val serviceId = brokerClient.getNextId()
      brokerClient.acceptAndServe(serviceId, TestService())

      // Server
      val connection = receiver.receiveBlockingWithTimeout(1000L)

      expectThat(connection) {
        get { serviceId }.isEqualTo(serviceId)
        get { address }.contains("plugins-")
        get { network }.isEqualTo("unix")
      }
    }

    test("acceptAndServe throws a CancellationException when the broker server is not running") {
      // Client
      brokerClient.start()

      // Server
      brokerServer.shutdownAndAwait()

      val serviceId = brokerClient.getNextId()
      expectThrows<CancellationException> {
        brokerClient.acceptAndServe(serviceId, TestService())
      }
    }

    test("dial creates a channel for a service id") {
      // Client
      brokerClient.start()

      // Server
      val serviceId = 1
      val server = channelProvider.server("unix", TestService()).start()
      val connection = ConnInfo.newBuilder()
        .setServiceId(serviceId)
        .setNetwork("unix")
        .setAddress(server.listenSocket.path())
        .build()
      sender.sendBlocking(connection)

      // Client
      val channel = brokerClient.dial(serviceId)
      val testClient = TestClient(channel)
      val response = testClient.sendMessage("you used to call me on my cell phone")

      expectThat(response.message).isEqualTo("[received] you used to call me on my cell phone")

      channel.shutdownAndAwait()
    }

    test("dial can be called before connection info has been sent") {
      // Server
      val serviceId = 1

      // Client
      brokerClient.start()
      val promise = scope.future { brokerClient.dial(serviceId) }

      // Server
      val server = channelProvider.server("unix", TestService()).start()
      val connection =
        ConnInfo.newBuilder()
          .setServiceId(serviceId)
          .setNetwork("unix")
          .setAddress(server.listenSocket.path())
          .build()
      sender.sendBlocking(connection)

      // Client
      val channel = promise.get()
      val testClient = TestClient(channel)
      val response = testClient.sendMessage("i know when that hotline bling")

      expectThat(response.message).isEqualTo("[received] i know when that hotline bling")

      channel.shutdownAndAwait()
    }
  }

  private class Fixture {
    val scope = CoroutineScope(Dispatchers.IO)
    val sender = Channel<ConnInfo>()
    val receiver = Channel<ConnInfo>()

    val channelProvider = ChannelProvider()

    val brokerServer = channelProvider.server(
      "unix",
      BrokerService(scope, sender, receiver)
    ).also { it.start() }

    val brokerChannel = channelProvider.clientChannel("unix", brokerServer.listenSocket.path())

    val brokerClient = Broker(scope, channelProvider, brokerChannel, 1000L)
  }

  private class BrokerService(
    private val scope: CoroutineScope,
    private val sender: Channel<ConnInfo>,
    private val receiver: Channel<ConnInfo>
  ) : GRPCBrokerGrpcKt.GRPCBrokerCoroutineImplBase() {
    override fun startStream(requests: Flow<ConnInfo>): Flow<ConnInfo> {
      scope.launch { requests.collect { connection -> receiver.send(connection) } }

      return flow { sender.consumeEach { connection -> emit(connection) } }
    }
  }
}
