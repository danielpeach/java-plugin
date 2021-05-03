package io.github.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.io.File
import java.nio.file.Files
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo

class ManagerTest : JUnit5Minutests {
  fun tests() =
      rootContext<Manager> {
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

        test("starts a plugin") {
          val path = Files.createTempFile("manager-test", ".sh")

          val file = File(path.toUri())

          file.writeText(
              """
        #!/bin/sh
        echo "1|1|unix|/path/to/socket|grpc"
      """.trimIndent())
          file.setExecutable(true)
          file.deleteOnExit()

          val (_, handshake) =
              startAndReadHandshake(
                  ClientConfig(
                      cmd = listOf("sh", "-c", path.toString()),
                      handshakeConfig =
                          HandshakeConfig(
                              magicCookieKey = "key",
                              magicCookieValue = "value",
                              protocolVersion = 1)))

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
