package io.github.danielpeach.plugin.example.bidirectional

interface Counter {
  fun put(key: String, value: Long, adder: (a: Long, b: Long) -> Long)
  fun get(key: String): Long
}
