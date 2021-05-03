package io.github.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.github.plugin.internal.TestClient
import io.github.plugin.internal.TestService
import strikt.api.expectThat
import strikt.assertions.isEqualTo

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
      val (key, cert) = generateCert()

      val provider =
        ChannelProvider(
          MTLSConfig(
            serverCertificate = cert,
            clientCertificate = cert,
            clientKey = key,
          )
        )

      val server = provider.server("unix", TestService())
      server.start()

      val channel = provider.clientChannel("unix", server.listenSocket.path())
      val client = TestClient(channel)

      val response = client.sendMessage("i must have called a thousand times")
      expectThat(response.message).isEqualTo("[received] i must have called a thousand times")

      server.shutdownAndAwait()
      channel.shutdownAndAwait()
    }
  }
}
