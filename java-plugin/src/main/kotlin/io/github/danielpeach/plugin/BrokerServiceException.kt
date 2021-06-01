package io.github.danielpeach.plugin

import kotlin.coroutines.cancellation.CancellationException

/**
 * Thrown when the client-side [Broker] cannot communicate with the plugin server.
 * */
class BrokerServiceException(error: Throwable) : CancellationException(error.message)
