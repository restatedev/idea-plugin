package my.example;

import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.sdk.endpoint.Endpoint;
import dev.restate.sdk.http.vertx.RestateHttpServer;

@Service
public class Greeter {

  @Handler
  public String greet(Context ctx, String name) {
    return "You said hi to " + name + "!";
  }

  public static void main(String[] args) {
    RestateHttpServer.listen(Endpoint.bind(new Greeter()));
  }
}
