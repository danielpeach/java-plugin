package io.github.danielpeach.plugin

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.time.withTimeout
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.*

internal const val CORE_PROTOCOL_VERSION = 1

/**
 * [Manager] is the entrypoint for starting a plugin and retrieving a plugin [Client].
 * */
class Manager {
  // mutex protects access to clients.
  private val mutex = Mutex()
  private val clients = mutableSetOf<Client>()

  private val logger: Logger = LoggerFactory.getLogger(javaClass)

  init {
    Runtime.getRuntime()
      .addShutdownHook(
        object : Thread() {
          override fun run() {
            killClients()
          }
        })
  }

  /**
   * Starts a plugin with the provided [ClientConfig].
   * @returns a client for the running plugin.
   * */
  fun start(clientConfig: ClientConfig): Client = runBlocking {
    val (key, cert) = if (clientConfig.encryptionMode == AutoMTLS) {
      generateCert()
    } else null to null

    val (process, handshake) = startAndReadHandshake(clientConfig, cert)

    val plugins = getNegotiatedPlugins(clientConfig, handshake)

    val mTLSConfig = if (clientConfig.encryptionMode == AutoMTLS) {
      getMTLSConfig(handshake, key, cert)
    } else null

    val client = Client(clientConfig, handshake, process, plugins, mTLSConfig)

    mutex.withLock { clients.add(client) }

    return@runBlocking client
  }

  /**
   * Blocks until all of [Manager]'s clients have been killed.
   * */
  fun killClients() = runBlocking {
    mutex.withLock {
      val removed = clients.map {
        async {
          it.kill()
          it
        }
      }.awaitAll()
      clients.removeAll(removed)
    }
  }

  internal fun startAndReadHandshake(
    config: ClientConfig,
    cert: X509Certificate? = null
  ): Pair<Process, Handshake> {
    val process = startProcess(config, cert)
    val unparsed = readLineWithTimeout(config.startTimeout, process.inputStream)
    val handshake = parseHandshake(unparsed)
    return process to handshake
  }

  private fun startProcess(config: ClientConfig, cert: X509Certificate?): Process {
    val handshakeConfig = config.handshakeConfig

    val env = mutableMapOf(
      handshakeConfig.magicCookieKey to handshakeConfig.magicCookieValue,
      "PLUGIN_PROTOCOL_VERSIONS" to
        config.getVersionedPlugins().keys.sorted().joinToString(","),
    )

    if (cert != null) {
      env["PLUGIN_CLIENT_CERT"] = writeCertAsPEM(cert)
    }

    val builder = ProcessBuilder(config.cmd).apply {
      env.forEach { (key, value) -> environment()[key] = value }
    }

    return builder.start()
  }

  private fun readLineWithTimeout(duration: Duration, reader: InputStream) = runBlocking {
    withTimeout(duration) { reader.bufferedReader().use { it.readLine() } }
  }

  private fun getMTLSConfig(
    handshake: Handshake,
    clientKey: PrivateKey?,
    clientCert: X509Certificate?
  ): MTLSConfig {
    val modeName = AutoMTLS::class.simpleName

    require(handshake.serverCertificate != null) {
      "Encryption mode is \"$modeName\" but did not receive server certificate."
    }
    require(clientKey != null) {
      "Encryption mode is \"$modeName\" but private key is null. This is a bug."
    }
    require(clientCert != null) {
      "Encryption mode is \"$modeName\" but cert is null. This is a bug."
    }

    return MTLSConfig(
      trustedCertificates = listOf(handshake.serverCertificate),
      certificate = clientCert,
      key = clientKey
    )
  }

  internal fun parseHandshake(handshake: String): Handshake {
    val split = handshake.split("|")

    val core = try {
      split[0].toInt()
    } catch (e: Exception) {
      throw IllegalArgumentException("Error parsing core protocol version: $e")
    }
    if (core != CORE_PROTOCOL_VERSION) {
      throw IllegalArgumentException(
        "Incompatible core API version with plugin. " +
          "Plugin version: $core, core version: $CORE_PROTOCOL_VERSION."
      )
    }

    val pluginVersion = try {
      split[1].toInt()
    } catch (e: Exception) {
      throw IllegalArgumentException("Error parsing plugin version: $e")
    }

    val networkType = split[2]
    if (networkType != "unix") {
      throw IllegalArgumentException(
        "Unsupported network type '$networkType'. Only 'unix' is supported."
      )
    }

    val networkAddress = split[3]
    if (networkAddress.contains("localhost")) {
      logger.warn(
        "Saw network address that contains 'localhost' ($networkAddress) - this is likely an error."
      )
    }

    val protocol = split[4]
    if (protocol != "grpc") {
      throw IllegalArgumentException(
        "Unsupported protocol type '$protocol'. Only 'grpc' is supported."
      )
    }

    val serverCertificate = split.getOrNull(5)?.let {
      if (it == "") return@let null

      val cert = parseEncodedCertificate(it)
      cert.checkValidity()
      cert
    }

    return Handshake(
      core = core,
      pluginVersion = pluginVersion,
      networkType = networkType,
      networkAddress = networkAddress,
      protocol = protocol,
      serverCertificate = serverCertificate
    )
  }

  private fun getNegotiatedPlugins(config: ClientConfig, handshake: Handshake): List<Plugin<*>> {
    val versioned = config.getVersionedPlugins()
    return versioned[handshake.pluginVersion]
      ?: throw IllegalStateException(
        "Plugin is incompatible with client. " +
          "Plugin version: ${handshake.pluginVersion}, client versions: ${versioned.keys.sorted().joinToString(", ")}"
      )
  }
}
