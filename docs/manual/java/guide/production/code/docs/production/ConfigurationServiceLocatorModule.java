package docs.production;

//#content
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.client.ConfigurationServiceLocator;
import com.typesafe.config.Config;
import play.Environment;

public class ConfigurationServiceLocatorModule extends AbstractModule {

    private final Environment environment;
    private final Config configuration;

    public ConfigurationServiceLocatorModule(Environment environment, Config configuration) {
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
