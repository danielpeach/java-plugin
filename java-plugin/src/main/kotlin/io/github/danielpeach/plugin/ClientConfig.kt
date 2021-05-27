package io.github.danielpeach.plugin

import java.io.Writer
import java.lang.IllegalArgumentException
import java.time.Duration

/**
 * An instance of [ClientConfig] tells a [Manager] how to configure a plugin.
 *
 * @see [Manager]
 *
 * @property [cmd] The command to be used to start the plugin subprocess.
 *
 * @property [handshakeConfig] A handshake that sets the requested plugin version
 * and a key/value verification that must match the server's handshake config.
 *
 * @property [plugins] The list of [Plugin]s that the client will have access to.
 *
 * @property [versionedPlugins] A set of integer-versioned [Plugin]s. This should
 * be set instead of [plugins] if there is a range of plugin versions acceptable to the client.
 *
 * @property [startTimeout] The maximum time allowed by the client for the plugin subprocess to start.
 *
 * @property [stdioMode] Defines how server-side plugin logs should be handled by the client.
 *
 * @property [encryptionMode] Defines the encryption mode for communication between the client and plugin.
 * */
data class ClientConfig(
  internal val cmd: List<String>,
  internal val handshakeConfig: HandshakeConfig,
  private val plugins: List<Plugin<*>> = emptyList(),
  private val versionedPlugins: Map<Int, List<Plugin<*>>> = emptyMap(),
  internal val startTimeout: Duration = Duration.ofMinutes(1),
  internal val stdioMode: StdioMode = Log,
  internal val encryptionMode: EncryptionMode = Plaintext
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

  internal fun getVersionedPlugins(): Map<Int, List<Plugin<*>>> {
    if (handshakeConfig.protocolVersion != null && plugins.isNotEmpty()) {
      return versionedPlugins + mapOf(handshakeConfig.protocolVersion to plugins)
    }
    return versionedPlugins
  }
}

/**
 * [HandshakeConfig] configures the pre-startup handshake between client and server.
 *
 * @property [protocolVersion] The version that clients must match on to agree they
 * can communicate. This field is not required if [ClientConfig.versionedPlugins] are being used.
 *
 * @property [magicCookieKey] Used with [magicCookieValue], this provides a very
 * basic verification that a plugin is intended to be launched. It is not intended to be a security feature.
 *
 * @property [magicCookieValue] Used with [magicCookieKey].
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
 * The [PipeToWriter] stdio mode forwards server-side plugin logs to instances of [Writer].
 *
 * @property [syncStdout] A writer for the plugin's stdout channel.
 *
 * @property [syncStderr] A writer for the plugin's stderr channel.
 * */
class PipeToWriter(
  val syncStdout: Writer,
  val syncStderr: Writer
) : StdioMode()

/**
 * The [Log] stdio mode logs server-side plugin logs alongside the client's logs.
 * */
object Log : StdioMode()

/**
 * The [Drop] stdio mode ignores server-side plugin logs.
 * */
object Drop : StdioMode()

/**
 * [EncryptionMode] defines how communication between the client and server is encrypted.
 * */
sealed class EncryptionMode

/**
 * When using the [Plaintext] encryption mode, communication between client and server will not be encrypted.
 * */
object Plaintext : EncryptionMode()

/**
 * When using the [AutoMTLS] encryption mode, the client and server will automatically negotiate mTLS.
 * */
object AutoMTLS : EncryptionMode()
