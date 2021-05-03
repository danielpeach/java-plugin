package io.github.plugin

import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.netty.channel.epoll.EpollDomainSocketChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTimedValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val MAC_OS = "Mac OS X"
private const val LINUX = "Linux"
private const val UNIX = "unix"

// https://stackoverflow.com/questions/54179843/how-to-create-a-grpc-service-over-a-local-socket-rather-then-inet-in-scala-java
internal class ChannelProvider(private val mTLSConfig: MTLSConfig? = null) {
  private val logger: Logger = LoggerFactory.getLogger(javaClass)

  fun clientChannel(network: String, address: String): ManagedChannel {
    if (network != UNIX) {
      throw UnsupportedNetworkException("Network type '${network}' is not supported.")
    }

    val (builder, shutdownHook) =
        when (val os = System.getProperty("os.name")) {
          MAC_OS -> {
            val kqg = KQueueEventLoopGroup()
            val builder =
                NettyChannelBuilder.forAddress(DomainSocketAddress(address))
                    .eventLoopGroup(kqg)
                    .channelType(KQueueDomainSocketChannel::class.java)

            builder to { kqg.shutdownGracefully() }
          }
          LINUX -> {
            val elg = EpollEventLoopGroup()
            val builder =
                NettyChannelBuilder.forAddress(DomainSocketAddress(address))
                    .eventLoopGroup(elg)
                    .channelType(EpollDomainSocketChannel::class.java)

            builder to { elg.shutdownGracefully() }
          }
          else -> throw UnsupportedPlatformException("OS '${os}' is not supported.")
        }

    if (mTLSConfig != null) {
      builder.sslContext(
          GrpcSslContexts.forClient()
              .trustManager(fingerprintTrustManagerFactoryForCert(mTLSConfig.serverCertificate))
              .keyManager(mTLSConfig.clientKey, mTLSConfig.clientCertificate)
              .build())
    } else {
      builder.usePlaintext()
    }

    return PluginChannel(builder.build()) { shutdownHook() }
  }

  private val serverDir =
      Files.createTempDirectory("plugins-").also { path ->
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
          *services)
    }
    logger.debug(
        "Took ${timed.duration.inMilliseconds} milliseconds to create server of type '$network'")
    return timed.value
  }

  private fun server(network: String, address: String, vararg services: BindableService): Server {
    if (network != UNIX) {
      throw UnsupportedNetworkException("Network type '${network}' is not supported.")
    }

    val (builder, shutdownHook) =
        when (val os = System.getProperty("os.name")) {
          MAC_OS -> {
            val boss = KQueueEventLoopGroup()
            val worker = KQueueEventLoopGroup()

            val builder =
                NettyServerBuilder.forAddress(DomainSocketAddress(address))
                    .bossEventLoopGroup(boss)
                    .workerEventLoopGroup(worker)
                    .channelType(KQueueServerDomainSocketChannel::class.java)

            builder to
                {
                  boss.shutdownGracefully()
                  worker.shutdownGracefully()
                }
          }
          LINUX -> {
            val boss = EpollEventLoopGroup()
            val worker = EpollEventLoopGroup()

            val builder =
                NettyServerBuilder.forAddress(DomainSocketAddress(address))
                    .bossEventLoopGroup(boss)
                    .workerEventLoopGroup(worker)
                    .channelType(EpollServerDomainSocketChannel::class.java)

            builder to
                {
                  boss.shutdownGracefully()
                  worker.shutdownGracefully()
                }
          }
          else -> throw UnsupportedPlatformException("OS '${os}' is not supported.")
        }

    if (mTLSConfig != null) {
      GrpcSslContexts.configure(
              SslContextBuilder.forServer(mTLSConfig.clientKey, mTLSConfig.clientCertificate))
          .trustManager(fingerprintTrustManagerFactoryForCert(mTLSConfig.serverCertificate))
          .trustManager(InsecureTrustManagerFactory.INSTANCE)
          .build()
          .also { builder.sslContext(it) }
    }

    services.forEach { builder.addService(it) }

    return PluginServer(builder.build(), DomainSocketAddress(address)) { shutdownHook() }
  }
}

internal data class MTLSConfig(
    val serverCertificate: X509Certificate,
    val clientCertificate: X509Certificate,
    val clientKey: PrivateKey
)

class UnsupportedNetworkException(message: String) : IllegalArgumentException(message)

class UnsupportedPlatformException(message: String) : IllegalArgumentException(message)
