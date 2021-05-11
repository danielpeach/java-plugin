package io.github.plugin

import io.grpc.ManagedChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalArgumentException

/**
 * An instance of [Client] is responsible for the lifecycle of client and server-side resources and
 * for dispensing interfaces implemented by the plugin framework.
 *
 * A [Client] should be instantiated with a [Manager].
 * */
class Client internal constructor(
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

  /**
   * Returns a type [T] that is implemented by the server-side subprocess.
   *
   * @param [T] The type that should be instantiated and returned.
   *
   * @returns A type [T] that is implemented by the server-side subprocess.
   * */
  inline fun <reified T> dispense(): T {
    val plugin = plugins.filterIsInstance<Plugin<T>>().firstOrNull()
      ?: throw IllegalArgumentException("Could not find plugin of type \"${T::class.java.name}\"")
    return plugin.client(channel, broker)
  }

  /**
   * Kills the plugin subprocess and cleans up client resources.
   * */
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

  // mutex protects access to alive.
  private val mutex = Mutex()
  private var alive = true

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
