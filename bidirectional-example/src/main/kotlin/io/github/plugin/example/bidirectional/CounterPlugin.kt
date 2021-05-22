package io.github.plugin.example.bidirectional

import io.github.plugin.Broker
import io.github.plugin.Plugin
import io.github.plugin.example.bidirectional.grpc.AddHelperGrpcKt
import io.github.plugin.example.bidirectional.grpc.Bidirectional
import io.github.plugin.example.bidirectional.grpc.CounterGrpcKt
import io.grpc.ManagedChannel
import kotlinx.coroutines.runBlocking

class CounterPlugin : Plugin<Counter> {
  override fun client(channel: ManagedChannel, broker: Broker): Counter {
    return CounterImpl(channel, broker)
  }
}

class CounterImpl(channel: ManagedChannel, private val broker: Broker) : Counter {
  private val stub = CounterGrpcKt.CounterCoroutineStub(channel)

  override fun get(key: String): Long = runBlocking {
    val request = Bidirectional.GetRequest.newBuilder().setKey(key).build()
    stub.get(request).value
  }

  override fun put(key: String, value: Long, adder: (a: Long, b: Long) -> Long): Unit = runBlocking {
    val service = AddHelperService(adder)

    val serviceId = broker.getNextId()
    val server = broker.acceptAndServe(serviceId, service)

    val request =
      Bidirectional.PutRequest.newBuilder()
        .setKey(key)
        .setValue(value)
        .setAddServer(serviceId)
        .build()

    stub.put(request)
    server.shutdown()
  }
}

class AddHelperService(private val adder: (a: Long, b: Long) -> Long) : AddHelperGrpcKt.AddHelperCoroutineImplBase() {
  override suspend fun sum(request: Bidirectional.SumRequest): Bidirectional.SumResponse {
    val result = adder(request.a, request.b)
    return Bidirectional.SumResponse.newBuilder().setR(result).build()
  }
}
