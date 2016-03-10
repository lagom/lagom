package docs.home.pubsub;

import akka.NotUsed;

import akka.stream.javadsl.Source;

import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface SensorService2 extends Service {

  //#service-api
  ServiceCall<String, Source<Temperature, ?>, NotUsed> registerTemperatures();
  //#service-api

  ServiceCall<String, NotUsed, Source<Temperature, ?>> temperatureStream();

  @Override
  default Descriptor descriptor() {
    return named("/sensorservice").with(
        pathCall("/device/:id/temperature/stream", registerTemperatures()),
        pathCall("/device/:id/temperature/stream", temperatureStream())
    );
  }
}
