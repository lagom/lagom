package docs.services;

import akka.NotUsed;
import play.api.mvc.Handler;
import play.api.mvc.RequestHeader;
import play.api.routing.Router;
import play.api.routing.SimpleRouter;
import play.mvc.Http;
import play.routing.RoutingDsl;
import scala.PartialFunction;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import static play.mvc.Results.ok;

import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.pathCall;
import static com.lightbend.lagom.javadsl.api.ServiceAcl.path;
import static java.util.concurrent.CompletableFuture.completedFuture;
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;

public class AdditionalRouters {

    //#hello-service
    interface HelloWordService extends Service {

        ServiceCall<NotUsed, String> hello(String name);

        @Override
        default Descriptor descriptor() {
            return named("fileupload")
                    .withCalls(
                            pathCall("/api/hello/:name", this::hello)
                    )
                    .withAutoAcl(true)
                    .withServiceAcls(path("/api/files"));
        }
    }
    //#hello-service


    public static class HelloWordServiceImpl implements HelloWordService {
        @Override
        public ServiceCall<NotUsed, String> hello(String name) {
            return input -> completedFuture("Hello, " + name);
        }
    }


    //#file-upload-router
    class FileUploadRouter implements SimpleRouter {

        private final Router delegate;

        @Inject
        public FileUploadRouter(RoutingDsl routingDsl) {
            this.delegate = routingDsl
                    .POST("/api/files")
                    .routingTo(request -> {
                        // for the sake of simplicity, this implementation
                        // only returns a short message for each incoming request.
                        return ok("File(s) uploaded");
                    })
                    .build().asScala();
        }

        @Override
        public PartialFunction<RequestHeader, Handler> routes() {
            return delegate.routes();
        }
    }
    //#file-upload-router

    class SomePlayRouter implements SimpleRouter {
        @Override
        public PartialFunction<RequestHeader, Handler> routes() {
            throw new RuntimeException("Not implemented");
        }
    }

    public class HelloWorldModuleDI {
        // example to show API that supports DI (Guice)
        //#lagom-module-some-play-router-DI
        public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
            @Override
            protected void configure() {
                bindService(
                        HelloService.class, HelloServiceImpl.class,
                        additionalRouter(SomePlayRouter.class)
                );
            }
        }
        //#lagom-module-some-play-router-DI
    }

    public class HelloWorldModuleInstance {
        // example to show API that supports passing instance
        //#lagom-module-some-play-router-instance
        public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
            @Override
            protected void configure() {
                bindService(
                        HelloService.class, HelloServiceImpl.class,
                        additionalRouter(new SomePlayRouter())
                );
            }
        }
        //#lagom-module-some-play-router-instance
    }


    //#lagom-module-file-upload
    public class HelloWorldModule extends AbstractModule implements ServiceGuiceSupport {
        @Override
        protected void configure() {
            bindService(
                    HelloService.class, HelloServiceImpl.class,
                    additionalRouter(FileUploadRouter.class)
            );
        }
    }
    //#lagom-module-file-upload


}
