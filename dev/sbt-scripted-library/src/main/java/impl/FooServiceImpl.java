package impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import java.util.concurrent.CompletableFuture;
import api.FooService;

public class FooServiceImpl implements FooService {

    @Override
    public ServiceCall<NotUsed, NotUsed> foo() {
        return request -> CompletableFuture.completedFuture(NotUsed.getInstance());
    }
}
