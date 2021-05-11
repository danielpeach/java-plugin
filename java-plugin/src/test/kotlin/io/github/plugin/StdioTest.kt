package io.github.plugin

import awaitBlocking
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.github.plugin.GrpcStdio.StdioData
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.OutputStream
import java.io.Writer

class StdioTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after { stdioServer.shutdown() }

    test("plugin logs are streamed back to the client") {
      val waitForLog = CompletableDeferred<Unit>()
      val slot = slot<String>()

      val writer =
        mockk<Writer> { every { write(capture(slot)) } answers { waitForLog.complete(Unit) } }

      Stdio(
        scope,
        ClientConfig(
          cmd = emptyList(),
          handshakeConfig = HandshakeConfig(
            protocolVersion = 1, magicCookieKey = "", magicCookieValue = ""
          ),
          stdioMode = PipeToWriter(
            syncStdout = writer,
            syncStderr = mockk()
          ),
          plugins = emptyList()
        ),
        channel
      ).start()

      pluginLogger.sendBlocking("hello from the other side")

      waitForLog.awaitBlocking()

      expectThat(slot.captured).isEqualTo("hello from the other side")
    }
  }

  private class Fixture {
    val scope = CoroutineScope(Dispatchers.IO)
    val pluginLogger = Channel<String>()

    val stdioServer: Server =
      InProcessServerBuilder.forName("stdio_test")
        .addService(StdioServer(pluginLogger))
        .build()
        .start()

    val channel: ManagedChannel = InProcessChannelBuilder.forName("stdio_test").build()
  }

  private class StdioServer(
    private val pluginLogger: Channel<String>,
  ) : GRPCStdioGrpcKt.GRPCStdioCoroutineImplBase() {
    override fun streamStdio(request: Empty): Flow<StdioData> {
      return flow {
        pluginLogger.consumeEach { line ->
          emit(
            StdioData.newBuilder()
              .setChannel(StdioData.Channel.STDOUT)
              .setData(ByteString.readFrom(line.byteInputStream()))
              .build()
          )
        }
      }
    }
  }
}
