/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.pubsub;

import akka.NotUsed;

import akka.stream.javadsl.Source;

import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

// #service-api
public interface SensorService extends Service {

  ServiceCall<Temperature, NotUsed> registerTemperature(String id);

  ServiceCall<NotUsed, Source<Temperature, ?>> temperatureStream(String id);

  @Override
  default Descriptor descriptor() {
    return named("/sensorservice")
        .withCalls(
            pathCall("/device/:id/temperature", this::registerTemperature),
            pathCall("/device/:id/temperature/stream", this::temperatureStream));
  }
}
// #service-api
