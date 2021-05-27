package io.github.danielpeach.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.github.danielpeach.plugin.internal.TestClient
import io.github.danielpeach.plugin.internal.TestService
import io.grpc.StatusException
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

class ChannelProviderTest : JUnit5Minutests {
  fun tests() = rootContext {
    test("plaintext client <-> server") {
      val provider = ChannelProvider()

      val server = provider.server("unix", TestService())
      server.start()

      val channel = provider.clientChannel("unix", server.listenSocket.path())
      val client = TestClient(channel)

      val response = client.sendMessage("hello from the other side")
      expectThat(response.message).isEqualTo("[received] hello from the other side")

      server.shutdown()
      channel.shutdown()

      server.shutdownAndAwait()
      channel.shutdownAndAwait()
    }

    test("ssl client <-> server") {
      val (clientKey, clientCert) = generateCert()
      val (serverKey, serverCert) = generateCert()

      val clientChannelProvider = ChannelProvider(
        MTLSConfig(
          trustedCertificates = listOf(serverCert),
          certificate = clientCert,
          key = clientKey,
        )
      )

      val serverChannelProvider = ChannelProvider(
        MTLSConfig(
          trustedCertificates = listOf(clientCert),
          certificate = serverCert,
          key = serverKey,
        )
      )

      val server = serverChannelProvider.server("unix", TestService())
      server.start()

      val clientChannel = clientChannelProvider.clientChannel("unix", server.listenSocket.path())
      val client = TestClient(clientChannel)

      val response = client.sendMessage("i must have called a thousand times")
      expectThat(response.message).isEqualTo("[received] i must have called a thousand times")

      server.shutdownAndAwait()
      clientChannel.shutdownAndAwait()
    }

    test("client can't send message to untrusted server") {
      val (clientKey, clientCert) = generateCert()
      val (_, trustedCert) = generateCert()
      val (serverKey, serverCert) = generateCert()

      val clientChannelProvider = ChannelProvider(
        MTLSConfig(
          trustedCertificates = listOf(trustedCert),
          certificate = clientCert,
          key = clientKey,
        )
      )

      val serverChannelProvider = ChannelProvider(
        MTLSConfig(
          trustedCertificates = listOf(clientCert),
          certificate = serverCert,
          key = serverKey,
        )
      )

      val server = serverChannelProvider.server("unix", TestService())
      server.start()

      val clientChannel = clientChannelProvider.clientChannel("unix", server.listenSocket.path())
      val client = TestClient(clientChannel)

      expectCatching { client.sendMessage("to tell you i'm sorry") }
        .isFailure()
        .isA<StatusException>()

      server.shutdownAndAwait()
      clientChannel.shutdownAndAwait()
    }
  }
}
