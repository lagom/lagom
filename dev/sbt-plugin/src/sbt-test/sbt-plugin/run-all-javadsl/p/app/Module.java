
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.client.ServiceClientGuiceSupport;
import com.lightbend.lagom.javadsl.api.*;
import play.*;
import javax.inject.Inject;
import java.util.Date;
import java.io.*;
import java.util.Optional;

public class Module extends AbstractModule implements ServiceClientGuiceSupport {
    @Override
    protected void configure() {
        ServiceAcl pAcl = new ServiceAcl(Optional.empty(), Optional.of("/p"));
        ServiceAcl assetsAcl = new ServiceAcl(Optional.empty(), Optional.of("/assets/.*"));
        bindServiceInfo(ServiceInfo.of("p", pAcl, assetsAcl));
    }
}
