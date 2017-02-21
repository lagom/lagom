package docs.services;

import com.lightbend.lagom.javadsl.api.*;
import akka.NotUsed;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class EchoServiceImpl implements EchoService {


    public ServiceCall<String, String> echo() {
        return input-> completedFuture(input);
    }
}
