package io.github.plugin

import io.grpc.Server
import io.netty.channel.unix.DomainSocketAddress
import java.net.SocketAddress
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class PluginServer(
    private val delegate: Server,
    private val socketAddress: SocketAddress,
    private val onShutdown: () -> Unit
) : Server() {
  private val logger: Logger = LoggerFactory.getLogger(javaClass)

  override fun awaitTermination() = delegate.awaitTermination()

  override fun isShutdown() = delegate.isShutdown

  override fun isTerminated() = delegate.isTerminated

  override fun awaitTermination(timeout: Long, unit: TimeUnit?) =
      delegate.awaitTermination(timeout, unit)

  override fun start(): Server {
    measureTime { delegate.start() }.also { duration ->
      logger.debug("Took ${duration.inMilliseconds} milliseconds to start server.")
    }
    return this
  }

  override fun shutdown(): Server {
    delegate.shutdown()
    onShutdown()
    return this
  }

  override fun shutdownNow(): Server {
    delegate.shutdownNow()
    onShutdown()
    return this
  }

  override fun getListenSockets(): List<SocketAddress> = listOf(socketAddress)
}

internal val Server.listenSocket: DomainSocketAddress
  get() = (listenSockets.first() as DomainSocketAddress)

internal fun Server.shutdownAndAwait() {
  if (!isShutdown) {
    shutdown()
    awaitTermination(2, TimeUnit.SECONDS)
  }
}
