package io.github.plugin

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.github.plugin.grpc.GRPCControllerGrpcKt
import io.github.plugin.grpc.GrpcController
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import strikt.api.expectCatching
import strikt.assertions.isSuccess

class ControllerTest : JUnit5Minutests {
  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      if (!controllerServer.isShutdown) {
        controllerServer.shutdown()
      }
    }

    test("controller server receives shutdown request") {
      runBlocking {
        supervisorScope {
          launch {
            controllerClient.shutdown()
          }

          expectCatching {
            withTimeout(1000L) {
              shutdown.receive()
            }
          }.isSuccess()
        }
      }
    }

    test("controller client does not throw when server shuts down mid-request") {
      runBlocking {
        launch {
          shutdown.receive()
        }

        expectCatching {
          controllerClient.shutdown()
        }.isSuccess()
      }
    }
  }

  internal class Fixture {
    val shutdown = Channel<Boolean>()

    private val controllerService = ControllerService(shutdown)

    val controllerServer: Server = InProcessServerBuilder.forName("controller_test")
        .addService(controllerService)
        .build()
        .start()
        .also { server ->
          controllerService.shutdownHook = {
            server.shutdownNow()
            server.awaitTermination()
          }
        }

    val controllerClient = Controller(InProcessChannelBuilder.forName("controller_test").build())
  }

  internal class ControllerService(private val shutdown: Channel<Boolean>) : GRPCControllerGrpcKt.GRPCControllerCoroutineImplBase() {
    var shutdownHook: (() -> Unit)? = null

    override suspend fun shutdown(request: GrpcController.Empty): GrpcController.Empty {
      shutdown.send(true)
      shutdownHook?.invoke()
      return GrpcController.Empty.getDefaultInstance()
    }
  }
}
