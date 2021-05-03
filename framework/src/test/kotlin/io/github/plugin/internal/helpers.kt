import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal fun <T> Channel<T>.receiveBlockingWithTimeout(timeoutMillis: Long): T = runBlocking {
  withTimeout(timeoutMillis) { receive() }
}

internal fun <T> Deferred<T>.awaitBlocking(): T = runBlocking { await() }
