package io.github.plugin.example.kv

import io.github.plugin.AutoMTLS
import io.github.plugin.ClientConfig
import io.github.plugin.HandshakeConfig
import io.github.plugin.Manager

// When running this example from Intellij, be sure to run the buildGo Gradle task first:
// ./gradlew :key-value-example:buildGo
//
// You can also just run the example from Gradle:
// ./gradlew :key-value-example:run
fun main() {
  val manager = Manager()
  val client =
      manager.start(
          ClientConfig(
              encryptionMode = AutoMTLS,
              handshakeConfig =
                  HandshakeConfig(
                      magicCookieKey = "BASIC_PLUGIN",
                      magicCookieValue = "hello",
                      protocolVersion = 1,
                  ),
              cmd = listOf("./key-value-example/build/kv"),
              plugins = listOf(KeyValueStorePlugin())))
  val kvStore = client.dispense<KeyValueStore>()

  kvStore.put("what", "is up")

  val get = kvStore.get("what")

  println("what: $get")

  client.kill()
}
