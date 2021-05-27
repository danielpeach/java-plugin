package io.github.danielpeach.plugin.example.kv

import com.google.protobuf.ByteString
import io.github.danielpeach.plugin.Broker
import io.github.danielpeach.plugin.Plugin
import io.github.danielpeach.plugin.example.keyvalue.grpc.KVGrpcKt
import io.github.danielpeach.plugin.example.keyvalue.grpc.Kv
import io.grpc.ManagedChannel
import kotlinx.coroutines.runBlocking
import java.nio.charset.Charset

class KeyValueStorePlugin : Plugin<KeyValueStore> {
  override fun client(channel: ManagedChannel, broker: Broker): KeyValueStore {
    return KeyValueStoreGrpc(channel)
  }
}

class KeyValueStoreGrpc(channel: ManagedChannel) : KeyValueStore {

  private val stub = KVGrpcKt.KVCoroutineStub(channel)

  override fun put(key: String, value: String): Unit = runBlocking {
    val request = Kv.PutRequest.newBuilder()
      .setKey(key)
      .setValue(ByteString.copyFrom(value, Charset.defaultCharset()))
      .build()
    stub.put(request)
  }

  override fun get(key: String): String = runBlocking {
    val request = Kv.GetRequest.newBuilder().setKey(key).build()
    val get = stub.get(request)
    get.value.toString(Charset.defaultCharset())
  }
}
