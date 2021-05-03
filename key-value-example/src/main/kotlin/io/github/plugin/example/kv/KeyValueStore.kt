package io.github.plugin.example.kv

interface KeyValueStore {
  fun put(key: String, value: String)
  fun get(key: String): String
}
