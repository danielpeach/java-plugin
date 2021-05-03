package io.github.plugin

import io.grpc.ManagedChannel

interface Plugin<T> {
  fun client(channel: ManagedChannel, broker: Broker): T
}
