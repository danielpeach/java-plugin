package io.github.danielpeach.plugin

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.StringBuilder

private val l: Logger = LoggerFactory.getLogger("io.github.danielpeach.plugin.PluginLogger")

internal fun logPlugin(raw: String) {
  logWithLogger(l, raw)
}

sealed class LogLine

private data class StructuredLogLine(
  @JsonProperty("@level") val level: String,
  @JsonProperty("@message") val message: String,
  @JsonProperty("@timestamp") val timestamp: String
) : LogLine() {
  val pairs = mutableMapOf<String, String>()

  @JsonAnySetter
  fun setPair(key: String, value: String) {
    pairs[key.substringAfter("@")] = value
  }
}

private data class UnstructuredLogLine(val message: String) : LogLine()

private val mapper = jacksonObjectMapper()

internal fun logWithLogger(logger: Logger, raw: String) {
  val parsed = parse(raw)
  log(parsed, logger)
}

private fun parse(line: String): LogLine {
  return try {
    mapper.readValue<StructuredLogLine>(line)
  } catch (e: Exception) {
    return UnstructuredLogLine(line)
  }
}

private fun log(parsed: LogLine, logger: Logger) {
  when (parsed) {
    is UnstructuredLogLine -> logger.info(parsed.message)
    is StructuredLogLine -> {
      val formatted =
        StringBuilder().append(parsed.message).run {
          if (parsed.pairs.isNotEmpty()) {
            append(" [")
            parsed.pairs.entries.forEachIndexed { i, (key, value) ->
              append("$key=$value")
              if (i < parsed.pairs.size - 1) {
                append(", ")
              }
            }
            append("]")
          }
          toString()
        }

      when (parsed.level.toUpperCase()) {
        "DEBUG" -> logger.debug(formatted)
        "TRACE" -> logger.trace(formatted)
        "INFO" -> logger.info(formatted)
        "WARN" -> logger.warn(formatted)
        "ERROR" -> logger.error(formatted)
        else -> {
          logger.warn("Unknown log level '${parsed.level}'")
          logger.info(formatted)
        }
      }
    }
  }
}
