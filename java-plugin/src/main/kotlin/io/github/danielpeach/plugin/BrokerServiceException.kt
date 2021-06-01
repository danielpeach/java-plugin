package io.github.danielpeach.plugin

import io.grpc.StatusException
import java.lang.Exception
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thrown when the client-side [Broker] cannot communicate with the plugin server.
 * */
class BrokerServiceException(error: Throwable) : CancellationException(error.message)
