package io.github.plugin

import io.github.plugin.grpc.GRPCControllerGrpcKt
import io.github.plugin.grpc.GrpcController
import io.grpc.ManagedChannel
import io.grpc.StatusException

internal class Controller(channel: ManagedChannel) {
  private val stub = GRPCControllerGrpcKt.GRPCControllerCoroutineStub(channel)

  suspend fun shutdown() {
    val request = GrpcController.Empty.getDefaultInstance()
    try {
      stub.shutdown(request)
    } catch (e: StatusException) {}
  }
}
