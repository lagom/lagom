package docs.production;

//#content
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ConfigurationServiceLocator;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import play.Configuration;
import play.Environment;

public class ConfigurationServiceLocatorModule extends AbstractModule {

    private final Environment environment;
    private final Configuration configuration;

    public ConfigurationServiceLocatorModule(Environment environment, Configuration configuration) {
        this.environment = environment;
        this.configuration = configuration;
    }

    @Override
    protected void configure() {
        if (environment.isProd()) {
            bind(ServiceLocator.class).to(ConfigurationServiceLocator.class);
        }
    }
}
//#content
