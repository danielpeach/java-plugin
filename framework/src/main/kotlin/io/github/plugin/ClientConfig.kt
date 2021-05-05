package io.github.plugin

import java.io.Writer
import java.lang.IllegalArgumentException
import java.time.Duration

/**
 * [ClientConfig] configures plugins. It should be passed to [Manager.start].
 *
 * @property [cmd] the command to be used to start the plugin subprocess.
 *
 * @property [handshakeConfig] a handshake that sets the requested plugin version and a key/value verification that must match the server's handshake config.
 *
 * @property [plugins] the list of [Plugin]s that the client will have access to.
 *
 * @property [versionedPlugins] a set of integer-versioned [Plugin]s. This should be set instead of [plugins] if there is a range of plugin versions acceptable to the client.
 *
 * @property [startTimeout] the maximum time allowed by the client for the plugin subprocess to start.
 *
 * @property [stdioMode] defines how server-side plugin logs should be handled by the client.
 *
 * @property [encryptionMode] defines the encryption mode for communication between the client and plugin.
 * */
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

/**
 * [HandshakeConfig] is the configuration used by client and servers to
 * handshake before starting a plugin connection.
 *
 * @property [protocolVersion] the version that clients must match on to agree they can communicate. This field is not required if [ClientConfig.versionedPlugins] are being used.
 *
 * @property [magicCookieKey] used with [magicCookieValue], this provides a very basic verification that a plugin is intended to be launched.
 *
 * @property [magicCookieValue] used with [magicCookieKey].
 * */
data class HandshakeConfig(
  val protocolVersion: Int? = null,
  val magicCookieKey: String,
  val magicCookieValue: String
)

/**
 * [StdioMode] defines how server-side plugin logs should be handled by the client.
 * */
sealed class StdioMode

/**
 * The [PipeToWriter] mode forwards server-side plugin logs to [Writer]s.
 *
 * @property [syncStdout] a writer for the plugin's stdout channel.
 *
 * @property [syncStderr] a writer for the plugin's stderr channel.
 * */
class PipeToWriter(
  val syncStdout: Writer,
  val syncStderr: Writer
) : StdioMode()

/**
 * The [Log] mode logs server-side plugin logs alongside the client's logs.
 * */
object Log : StdioMode()

/**
 * The [Drop] mode ignores server-side plugin logs.
 * */
object Drop : StdioMode()

/**
 * [EncryptionMode] defines how communication between the client and server is encrypted.
 * */
sealed class EncryptionMode

/**
 * The [Plaintext] encryption mode does not encrypt client/server communication.
 * */
object Plaintext : EncryptionMode()

/**
 * The [AutoMTLS] encryption mode has the client and server automatically negotiate mTLS.
 * */
object AutoMTLS : EncryptionMode()
