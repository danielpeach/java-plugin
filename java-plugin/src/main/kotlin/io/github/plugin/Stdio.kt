package io.github.plugin

import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.lang.IllegalStateException
import io.github.plugin.GrpcStdio.StdioData.Channel as StdioChannel

internal class Stdio(
  private val scope: CoroutineScope,
  private val config: ClientConfig,
  channel: ManagedChannel,
) {
  private val stub = GRPCStdioGrpcKt.GRPCStdioCoroutineStub(channel)

  fun start() {
    val stdoutChannel = Channel<String>()
    val stderrChannel = Channel<String>()

    scope.launch { stream(stdoutChannel, stderrChannel) }

    scope.launch { handle(stdoutChannel, StdioChannel.STDOUT, config.stdioMode) }

    scope.launch { handle(stderrChannel, StdioChannel.STDERR, config.stdioMode) }
  }

  private suspend fun stream(stdoutChannel: Channel<String>, stderrChannel: Channel<String>) {
    val stream = stub.streamStdio(Empty.getDefaultInstance())
    stream.collect { s ->
      when (s.channel) {
        StdioChannel.INVALID -> throw IllegalStateException("Invalid data")
        StdioChannel.STDOUT -> stdoutChannel.send(s.data.toStringUtf8())
        StdioChannel.STDERR -> stderrChannel.send(s.data.toStringUtf8())
        else -> throw IllegalStateException("Unknown channel: ${s.channel}")
      }
    }
  }

  private suspend fun handle(channel: Channel<String>, type: StdioChannel, mode: StdioMode) {
    when (mode) {
      is Drop -> channel.consumeEach {}
      is PipeToWriter -> {
        val writer = (if (type == StdioChannel.STDOUT) mode.syncStdout else mode.syncStderr)
        writer.use { w -> channel.consumeEach { line -> w.write(line) } }
      }
      is Log -> {
        channel.consumeEach { line -> logPlugin(line) }
      }
    }
  }
}
