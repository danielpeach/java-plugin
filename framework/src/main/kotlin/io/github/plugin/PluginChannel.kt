package io.github.plugin

import io.grpc.CallOptions
import io.grpc.ManagedChannel
import io.grpc.MethodDescriptor
import java.util.concurrent.TimeUnit

internal class PluginChannel(
  private val delegate: ManagedChannel,
  private val onShutdown: () -> Unit,
) : ManagedChannel() {
  override fun authority() = delegate.authority()

  override fun awaitTermination(timeout: Long, unit: TimeUnit?) =
    delegate.awaitTermination(timeout, unit)

  override fun isShutdown() = delegate.isShutdown

  override fun isTerminated() = delegate.isTerminated

  override fun <RequestT : Any?, ResponseT : Any?> newCall(
    methodDescriptor: MethodDescriptor<RequestT, ResponseT>?,
    callOptions: CallOptions?
  ) = delegate.newCall(methodDescriptor, callOptions)

  override fun shutdown(): ManagedChannel {
    delegate.shutdown()
    onShutdown()
    return this
  }

  override fun shutdownNow(): ManagedChannel {
    delegate.shutdownNow()
    onShutdown()
    return this
  }
}

internal fun ManagedChannel.shutdownAndAwait() {
  if (!isShutdown) {
    shutdown()
    awaitTermination(2, TimeUnit.SECONDS)
  }
}
