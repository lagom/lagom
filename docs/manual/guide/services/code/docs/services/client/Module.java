package docs.services.client;

//#bind-hello-client
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.server.ServiceGuiceSupport;
import docs.services.HelloService;

public class Module extends AbstractModule implements ServiceGuiceSupport {

    protected void configure() {
        bindClient(HelloService.class);
    }
}
//#bind-hello-client
