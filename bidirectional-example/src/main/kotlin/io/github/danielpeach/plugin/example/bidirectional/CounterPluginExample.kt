package io.github.danielpeach.plugin.example.bidirectional

import io.github.danielpeach.plugin.ClientConfig
import io.github.danielpeach.plugin.HandshakeConfig
import io.github.danielpeach.plugin.Manager

// When running this example from Intellij, be sure to run the buildGo Gradle task first:
// ./gradlew :bidirectional-example:buildGo
//
// You can also just run the example from Gradle:
// ./gradlew :bidirectional-example:run
fun main() {
  val manager = Manager()
  val client = manager.start(
    ClientConfig(
      handshakeConfig = HandshakeConfig(
        magicCookieKey = "BASIC_PLUGIN",
        magicCookieValue = "hello",
        protocolVersion = 1
      ),
      cmd = listOf("./bidirectional-example/build/bidirectional"),
      plugins = listOf(CounterPlugin())
    )
  )

  val bidirectional = client.dispense<Counter>()

  bidirectional.put("my_key", 10) { a, b ->
    println("adding $a + $b")
    a + b
  }

  bidirectional.put("my_key", 20) { a, b ->
    println("multiplying $a * $b")
    a * b
  }

  val get = bidirectional.get("my_key")

  print("my_key: $get")

  client.kill()
}
