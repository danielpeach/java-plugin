package io.github.danielpeach.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class ManagerTest : JUnit5Minutests {
  fun tests() = rootContext<Manager> {
    fixture { Manager() }

    test("valid handshake") {
      val handshake = parseHandshake("1|1|unix|/path/to/socket|grpc")
      expectThat(handshake) {
        get { core }.isEqualTo(1)
        get { pluginVersion }.isEqualTo(1)
        get { networkType }.isEqualTo("unix")
        get { networkAddress }.isEqualTo("/path/to/socket")
        get { protocol }.isEqualTo("grpc")
      }
    }

    test("bad core protocol") {
      expectThrows<IllegalArgumentException> {
        parseHandshake("bad|1|unix|/path/to/socket|grpc")
      }

      expectThrows<IllegalArgumentException> {
        parseHandshake("20|1|unix|/path/to/socket|grpc")
      }
    }

    test("bad plugin version") {
      expectThrows<IllegalArgumentException> {
        parseHandshake("1|bad|unix|/path/to/socket|grpc")
      }
    }

    test("bad network type") {
      expectThrows<IllegalArgumentException> { parseHandshake("1|1|tcp|/path/to/socket|grpc") }
    }

    test("bad protocol") {
      expectThrows<IllegalArgumentException> { parseHandshake("1|1|unix|/path/to/socket|rpc") }
    }

    test("initiates a simple plugin handshake") {
      val path = write {
        """
        #!/bin/sh
        echo "1|1|unix|/path/to/socket|grpc" # ignore
        
        >&2 echo MAGIC_COOKIE_KEY=${"$"}MAGIC_COOKIE_KEY
        >&2 echo PLUGIN_PROTOCOL_VERSIONS=${"$"}PLUGIN_PROTOCOL_VERSIONS
        """
      }

      val (process, _) = startAndReadHandshake(
        ClientConfig(
          cmd = listOf("sh", "-c", path.toString()),
          handshakeConfig = HandshakeConfig(
            magicCookieKey = "MAGIC_COOKIE_KEY",
            magicCookieValue = "MAGIC_COOKIE_VALUE",
            protocolVersion = 1
          ),
          plugins = listOf(mockk())
        )
      )

      process.errorStream.bufferedReader().use {
        expectThat(it.readLine()).isEqualTo("MAGIC_COOKIE_KEY=MAGIC_COOKIE_VALUE")
        expectThat(it.readLine()).isEqualTo("PLUGIN_PROTOCOL_VERSIONS=1")
      }
    }

    test("initiates a complex plugin handshake") {
      val path = write {
        """
        #!/bin/sh
        echo "1|1|unix|/path/to/socket|grpc" # ignore
        
        >&2 echo PLUGIN_PROTOCOL_VERSIONS=${"$"}PLUGIN_PROTOCOL_VERSIONS
        """
      }

      val (process, _) = startAndReadHandshake(
        ClientConfig(
          cmd = listOf("sh", "-c", path.toString()),
          handshakeConfig = HandshakeConfig(
            magicCookieKey = "MAGIC_COOKIE_KEY",
            magicCookieValue = "MAGIC_COOKIE_VALUE",
            protocolVersion = 1
          ),
          versionedPlugins = mapOf(
            1 to listOf(mockk()),
            2 to listOf(mockk()),
            3 to listOf(mockk())
          )
        )
      )

      process.errorStream.bufferedReader().use {
        expectThat(it.readLine()).isEqualTo("PLUGIN_PROTOCOL_VERSIONS=1,2,3")
      }
    }

    test("can read a handshake from the started plugin") {
      val path = write {
        """
        #!/bin/sh
        echo "1|1|unix|/path/to/socket|grpc"
        """
      }

      val (_, handshake) = startAndReadHandshake(
        ClientConfig(
          cmd = listOf("sh", "-c", path.toString()),
          handshakeConfig = HandshakeConfig(
            magicCookieKey = "MAGIC_COOKIE_KEY",
            magicCookieValue = "MAGIC_COOKIE_VALUE",
            protocolVersion = 1
          ),
          plugins = listOf(mockk())
        )
      )

      expectThat(handshake) {
        get { core }.isEqualTo(1)
        get { pluginVersion }.isEqualTo(1)
        get { networkType }.isEqualTo("unix")
        get { networkAddress }.isEqualTo("/path/to/socket")
        get { protocol }.isEqualTo("grpc")
      }
    }
  }
}

fun write(block: () -> String): Path {
  val script = block().trimIndent()
  val path = Files.createTempFile("java-plugin-test", ".sh")

  val file = File(path.toUri())

  file.writeText(script)
  file.setExecutable(true)
  file.deleteOnExit()

  return path
}
