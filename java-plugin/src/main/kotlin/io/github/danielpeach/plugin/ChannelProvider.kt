package io.github.danielpeach.plugin

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.netty.channel.ChannelOption
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import io.netty.handler.ssl.SslContextBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue

private const val MAC_OS = "Mac OS X"
private const val LINUX = "Linux"
private const val UNIX = "unix"

// https://stackoverflow.com/questions/54179843/how-to-create-a-grpc-service-over-a-local-socket-rather-then-inet-in-scala-java
internal class ChannelProvider(private val mTLSConfig: MTLSConfig? = null) {
  private val logger: Logger = LoggerFactory.getLogger(javaClass)

  fun clientChannel(network: String, address: String): ManagedChannel {
    if (network != UNIX) {
      throw IllegalArgumentException("Network type '$network' is not supported.")
    }

    val builder = NettyChannelBuilder.forAddress(DomainSocketAddress(address))
      .withOption(ChannelOption.SO_KEEPALIVE, null)

    val shutdownHook = when (val os = System.getProperty("os.name")) {
      MAC_OS -> {
        val kqg = KQueueEventLoopGroup()

        builder
          .eventLoopGroup(kqg)
          .channelType(KQueueDomainSocketChannel::class.java);

        { kqg.shutdownGracefully() }
      }
      LINUX -> {
        val elg = EpollEventLoopGroup()

        builder
          .eventLoopGroup(elg)
          .channelType(EpollDomainSocketChannel::class.java);

        { elg.shutdownGracefully() }
      }
      else -> throw IllegalArgumentException("OS '$os' is not supported.")
    }

    if (mTLSConfig != null) {
      builder.sslContext(
        GrpcSslContexts.forClient()
          .trustManager(fingerprintTrustManagerFactoryForCerts(mTLSConfig.trustedCertificates))
          .keyManager(mTLSConfig.key, mTLSConfig.certificate)
          .build()
      )
    } else {
      builder.usePlaintext()
    }

    return PluginChannel(builder.build()) { shutdownHook() }
  }

  private val serverDir = Files.createTempDirectory("plugins-").also { path ->
    Runtime.getRuntime()
      .addShutdownHook(
        object : Thread() {
          override fun run() {
            path.toFile().deleteRecursively()
          }
        })
  }

  private val socketCounter = AtomicInteger(0)

  fun server(network: String, vararg services: BindableService): Server {
    val timed = measureTimedValue {
      server(
        network,
        Paths.get(serverDir.toString(), "server-${socketCounter.addAndGet(1)}").toString(),
        *services
      )
    }
    logger.debug(
      "Took ${timed.duration.inMilliseconds} milliseconds to create server of type '$network'"
    )
    return timed.value
  }

  private fun server(network: String, address: String, vararg services: BindableService): Server {
    if (network != UNIX) {
      throw IllegalArgumentException("Network type '$network' is not supported.")
    }

    val builder = NettyServerBuilder.forAddress(DomainSocketAddress(address))
      .withChildOption(ChannelOption.SO_KEEPALIVE, null)

    val shutdownHook = when (val os = System.getProperty("os.name")) {
      MAC_OS -> {
        val boss = KQueueEventLoopGroup()
        val worker = KQueueEventLoopGroup()

        builder
          .bossEventLoopGroup(boss)
          .workerEventLoopGroup(worker)
          .channelType(KQueueServerDomainSocketChannel::class.java);

        {
          boss.shutdownGracefully()
          worker.shutdownGracefully()
        }
      }
      LINUX -> {
        val boss = EpollEventLoopGroup()
        val worker = EpollEventLoopGroup()

        builder
          .bossEventLoopGroup(boss)
          .workerEventLoopGroup(worker)
          .channelType(EpollServerDomainSocketChannel::class.java);

        {
          boss.shutdownGracefully()
          worker.shutdownGracefully()
        }
      }
      else -> throw IllegalArgumentException("OS '$os' is not supported.")
    }

    if (mTLSConfig != null) {
      builder.sslContext(
        GrpcSslContexts.configure(SslContextBuilder.forServer(mTLSConfig.key, mTLSConfig.certificate)).build()
      )
    }

    services.forEach { builder.addService(it) }

    return PluginServer(builder.build(), DomainSocketAddress(address)) { shutdownHook() }
  }
}

internal data class MTLSConfig(
  val trustedCertificates: List<X509Certificate>,
  val certificate: X509Certificate,
  val key: PrivateKey
)
