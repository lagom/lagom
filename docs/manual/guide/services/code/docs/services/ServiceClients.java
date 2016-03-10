package docs.services;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import javax.inject.Inject;
import java.util.concurrent.CompletionStage;

public class ServiceClients {

  public interface MyService {
    ServiceCall<NotUsed, NotUsed, String> sayHelloLagom();
  }

  //#hello-consumer
  public class MyServiceImpl implements MyService {
    private final HelloService helloService;

    @Inject
    public MyServiceImpl(HelloService helloService) {
      this.helloService = helloService;
    }

    @Override
    public ServiceCall<NotUsed, NotUsed, String> sayHelloLagom() {
      return (id, msg) -> {
        CompletionStage<String> response = helloService.sayHello().invoke("Lagom");
        return response.thenApply(answer ->
            "Hello service said: " + answer
        );
      };
    }
  }
  //#hello-consumer

}
