/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.production;

// #content
import com.google.inject.AbstractModule;
import com.lightbend.lagom.javadsl.api.ServiceLocator;
import com.lightbend.lagom.javadsl.client.ConfigurationServiceLocator;
import play.Environment;
import com.typesafe.config.Config;

public class ConfigurationServiceLocatorModule extends AbstractModule {

  private final Environment environment;
  private final Config config;

  public ConfigurationServiceLocatorModule(Environment environment, Config config) {
    this.environment = environment;
    this.config = config;
  }

  @Override
  protected void configure() {
    if (environment.isProd()) {
      bind(ServiceLocator.class).to(ConfigurationServiceLocator.class);
    }
  }
}
// #content
