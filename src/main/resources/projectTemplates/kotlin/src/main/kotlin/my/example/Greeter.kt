package my.example

import dev.restate.sdk.annotation.*
import dev.restate.sdk.http.vertx.RestateHttpServer
import dev.restate.sdk.kotlin.*
import dev.restate.sdk.kotlin.endpoint.*

@Service
class Greeter {

  @Handler
  suspend fun greet(ctx: Context, name: String): String {
    return "You said hi to $name!";
  }
}

fun main() {
  RestateHttpServer.listen(endpoint {
    bind(Greeter())
  })
}