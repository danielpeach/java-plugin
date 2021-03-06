package io.github.danielpeach.plugin.internal

import io.github.danielpeach.plugin.test.grpc.Test
import io.github.danielpeach.plugin.test.grpc.TestServiceGrpcKt
import io.grpc.ManagedChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

internal class TestService(private val received: Channel<Boolean>? = null) :
  TestServiceGrpcKt.TestServiceCoroutineImplBase() {
  override suspend fun send(request: Test.TestMessage): Test.TestMessage {
    received?.send(true)
    return Test.TestMessage.newBuilder().setMessage("[received] ${request.message}").build()
  }
}

internal class TestClient(channel: ManagedChannel) {
  private val stub = TestServiceGrpcKt.TestServiceCoroutineStub(channel)

  fun sendMessage(message: String = "test message") = runBlocking {
    stub.send(Test.TestMessage.newBuilder().setMessage(message).build())
  }
}
