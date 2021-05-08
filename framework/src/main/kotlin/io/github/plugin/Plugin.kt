package io.github.plugin

import io.grpc.ManagedChannel

/**
 * [Plugin] is the primary interface for plugin developers to implement but should not be
 * accessed directly by plugin users.
 *
 * Plugin developers implement a type [T] that delegates to a server-side subprocess by
 * communicating over gRPC.
 *
 * @param [T] the type to be implemented.
 * */
interface Plugin<T> {

  /**
   * An implementation of [client] should return the interface implementation of [T].
   *
   * @param [channel] should be used by the interface implementation to communicate with
   * the plugin over gRPC. Generated gRPC client stubs for Java and Kotlin generally accept
   * a [ManagedChannel] as a constructor argument.
   *
   * @param [broker] is used to facilitate bi-directional communication between client and server. See [Broker].
   * */
  fun client(channel: ManagedChannel, broker: Broker): T
}
