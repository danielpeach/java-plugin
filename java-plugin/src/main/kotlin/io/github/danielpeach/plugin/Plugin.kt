package io.github.danielpeach.plugin

import io.grpc.ManagedChannel

/**
 * Plugin developers implement a type [T] that delegates to a server-side subprocess by
 * communicating over gRPC.
 *
 * [Plugin] is the primary interface for plugin developers to implement but should not be
 * accessed directly by plugin users.
 *
 * @param [T] The type to be implemented.
 * */
interface Plugin<T> {

  /**
   * An implementation of [client] should return the interface implementation of [T].
   *
   * @param [channel] Should be used by the interface implementation to communicate with
   * the plugin over gRPC.
   *
   * @param [broker] Used to facilitate bi-directional communication between client and server.
   *
   * @see [Broker]
   * */
  fun client(channel: ManagedChannel, broker: Broker): T
}
