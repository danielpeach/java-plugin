package io.github.plugin

import io.grpc.ManagedChannel
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Client
internal constructor(
    config: ClientConfig,
    handshake: Handshake,
    process: Process,
    @PublishedApi internal val plugins: List<Plugin<*>>,
    mTLSConfig: MTLSConfig? = null,
) {
  private val scope = CoroutineScope(Dispatchers.IO)

  @PublishedApi internal val channel: ManagedChannel
  @PublishedApi internal val broker: Broker
  private val controller: Controller
  private val stdio: Stdio

  init {
    // This reads the framework level logs, which are redirected to stderr.
    listenToStderr(process.errorStream)

    val channelProvider = ChannelProvider(mTLSConfig)
    channel = channelProvider.clientChannel(handshake.networkType, handshake.networkAddress)

    controller = Controller(channel)
    stdio = Stdio(scope, config, channel)
    broker = Broker(scope, channelProvider, channel)

    // This reads the plugin-level logs (usually, it's a little buggy) after
    // stdout and stderr have been redirected.
    stdio.start()

    broker.start()
  }

  inline fun <reified T> dispense(): T {
    val plugin = plugins.filterIsInstance<Plugin<T>>().first()
    return plugin.client(channel, broker)
  }

  // mutex protects access to alive.
  private val mutex = Mutex()
  private var alive = true

  fun kill(): Unit = runBlocking {
    mutex.withLock {
      if (alive) {
        scope.cancel()
        controller.shutdown()
        channel.shutdownAndAwait()
        alive = false
      }
    }
  }

  // Debug logs from the server side plugin framework itself will end up here.
  private fun listenToStderr(stderr: InputStream) {
    val reader = stderr.bufferedReader()
    scope.launch {
      try {
        while (isActive) {
          reader.use { it.readLine()?.also { line -> logPlugin(line) } }
        }
      } catch (e: IOException) {}
    }
  }
}
