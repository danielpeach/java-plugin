package io.github.plugin

import java.io.Writer
import java.lang.IllegalArgumentException
import java.time.Duration

data class ClientConfig(
  val cmd: List<String>,
  val handshakeConfig: HandshakeConfig,
  private val plugins: List<Plugin<*>> = emptyList(),
  private val versionedPlugins: Map<Int, List<Plugin<*>>> = emptyMap(),
  val startTimeout: Duration = Duration.ofMinutes(1),
  val stdioMode: StdioMode = Log,
  val encryptionMode: EncryptionMode = Plaintext
) {
  init {
    if (handshakeConfig.protocolVersion != null &&
      versionedPlugins[handshakeConfig.protocolVersion] != null &&
      plugins.isNotEmpty()
    ) {
      throw IllegalArgumentException(
        "versionedPlugins[${handshakeConfig.protocolVersion}] will be overwritten. " +
          "Either supply an empty plugins list or remove versionedPlugins[${handshakeConfig.protocolVersion}]."
      )
    }

    if (handshakeConfig.protocolVersion == null && versionedPlugins.isEmpty()) {
      throw IllegalArgumentException("One of protocol version or versioned plugins must be set.")
    }
  }

  fun getVersionedPlugins(): Map<Int, List<Plugin<*>>> {
    if (handshakeConfig.protocolVersion != null && plugins.isNotEmpty()) {
      return versionedPlugins + mapOf(handshakeConfig.protocolVersion to plugins)
    }
    return versionedPlugins
  }
}

data class HandshakeConfig(
  val protocolVersion: Int? = null,
  val magicCookieKey: String,
  val magicCookieValue: String
)

sealed class StdioMode

class PipeToWriter(val syncStdout: Writer, val syncStderr: Writer) : StdioMode()

object Log : StdioMode()

object Drop : StdioMode()

sealed class EncryptionMode

object Plaintext : EncryptionMode()

object AutoMTLS : EncryptionMode()
