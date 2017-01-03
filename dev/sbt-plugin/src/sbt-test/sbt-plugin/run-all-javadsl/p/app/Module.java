
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import api.BarService;
import api.FooService;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;

public class Module extends AbstractModule implements ServiceClientGuiceSupport {
    @Override
    protected void configure() {
        ServiceAcl pAcl = new ServiceAcl(Optional.empty(), Optional.of("/p"));
        ServiceAcl assetsAcl = new ServiceAcl(Optional.empty(), Optional.of("/assets/.*"));

        bindServiceInfo(ServicInfo.of("p", pAcl, assetsAcl));
        bindClient(FooService.class);
        bind(OnStart.class).asEagerSingleton();
    }
}
