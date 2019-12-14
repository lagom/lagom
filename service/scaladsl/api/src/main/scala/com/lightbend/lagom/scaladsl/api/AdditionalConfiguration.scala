/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package com.lightbend.lagom.scaladsl.api

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import play.api.Configuration
import play.api.Environment

/**
 * This trait allows Lagom integrations to define additional configuration that gets mixed into the application trait.
 *
 * By extending this, and overriding `additionalConfiguration`, an integration can inject configuration, and the user
 * can control which order this configuration gets applied by changing the order in which traits are mixed together.
 */
trait ProvidesAdditionalConfiguration {
  /**
   * Define the additional configuration to add to the application.
   *
   * Classes that override this must combine the configuration they add with the configuration from the super
   * implementation. Failure to do this will prevent different integrations from working with each other.
   *
   * When overriding, the overridden file should be a def, so as to ensure multiple components can all override it.
   * Lagom will only invoke this method once from a lazy val, so it will effectively be calculated once.
   */
  def additionalConfiguration: AdditionalConfiguration = AdditionalConfiguration.empty

  /**
   * Define the additional configuration to add to the application, provided a reference to the application environment
   * and the result of any additional configuration overrides applied to the initial application configuration in the
   * static method above.
   *
   * Classes that override this must combine the configuration they add with the configuration from the super
   * implementation. Failure to do this will prevent different integrations from working with each other.
   *
   * When overriding, the overridden file should be a def, so as to ensure multiple components can all override it.
   * Lagom will only invoke this method once from a lazy val, so it will effectively be calculated once.
   *
   * @param environment The application environment.
   * @param previousConfiguration A reference to the initial application configuration combined with any additional
   *                              configuration overrides added by the static method above.
   */
  def additionalConfiguration(
      environment: Environment,
      previousConfiguration: Configuration
  ): AdditionalConfiguration = AdditionalConfiguration.empty

  /**
   * Resolves all additional configuration into a single [[Configuration]].
   * @param environment The application environment.
   * @param initialConfiguration A reference to the initial application configuration prior to any overrides.
   * @return A [[Configuration]] containing all resolved additional configuration.
   */
  private[lagom] def resolveAdditionalConfiguration(
      environment: Environment,
      initialConfiguration: Configuration
  ): Configuration = {
    val additionalConfig = additionalConfiguration.configuration
    new Configuration(
      additionalConfiguration(
        environment,
        new Configuration(additionalConfig.withFallback(initialConfiguration.underlying))
      ).configuration.withFallback(additionalConfig)
    )
  }
}

/**
 * Additional configuration that will be added to the main system configuration.
 */
final class AdditionalConfiguration private (private[lagom] val configuration: Config) {
  /**
   * Add configuration to the additional configuration.
   */
  def ++(configurationToAdd: Config): AdditionalConfiguration = {
    new AdditionalConfiguration(configuration.withFallback(configurationToAdd))
  }
}

object AdditionalConfiguration {
  private[lagom] val empty = new AdditionalConfiguration(ConfigFactory.empty)
}
