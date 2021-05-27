package io.github.danielpeach.plugin

import io.grpc.StatusException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thrown when the client-side [Broker] cannot communicate with the plugin server.
 * */
class BrokerServiceException(se: StatusException) : CancellationException(se.message)
