package io.github.plugin

import io.grpc.StatusException
import kotlin.coroutines.cancellation.CancellationException

class BrokerServiceException(se: StatusException) : CancellationException(se.message)
