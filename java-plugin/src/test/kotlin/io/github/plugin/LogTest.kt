package io.github.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.*
import org.slf4j.Logger
import strikt.api.expectThat
import strikt.assertions.endsWith

class LogTest : JUnit5Minutests {

  fun tests() = rootContext {
    test("unstructured log line") {
      val slot = slot<String>()
      val logger = mockk<Logger> { every { info(capture(slot)) } just Runs }

      logWithLogger(logger, "unstructured log line")

      expectThat(slot.captured).endsWith("unstructured log line")
    }

    test("simple JSON log") {
      val slot = slot<String>()
      val logger = mockk<Logger> { every { debug(capture(slot)) } just Runs }

      logWithLogger(
        logger,
        """{"@level": "debug", "@message": "simple JSON log", "@timestamp": "2021-03-17T12:09:37.551Z" }"""
      )

      expectThat(slot.captured).endsWith("simple JSON log")
    }

    test("complex JSON log") {
      val slot = slot<String>()
      val logger = mockk<Logger> { every { debug(capture(slot)) } just Runs }

      val input =
        """{"@level": "debug", "@message": "complex JSON log", "@timestamp": "2021-03-17T12:09:37.551Z", "@key1": "value1", "@key2": "value2" }"""
      logWithLogger(logger, input)

      expectThat(slot.captured).endsWith("complex JSON log [key1=value1, key2=value2]")
    }
  }
}
