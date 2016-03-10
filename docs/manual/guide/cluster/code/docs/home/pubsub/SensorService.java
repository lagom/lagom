package docs.home.pubsub;

import akka.NotUsed;

import akka.stream.javadsl.Source;

import com.lightbend.lagom.javadsl.api.*;
import static com.lightbend.lagom.javadsl.api.Service.*;

//#service-api
public interface SensorService extends Service {

  ServiceCall<String, Temperature, NotUsed> registerTemperature();

  ServiceCall<String, NotUsed, Source<Temperature, ?>> temperatureStream();

  @Override
  default Descriptor descriptor() {
    return named("/sensorservice").with(
      pathCall("/device/:id/temperature", registerTemperature()),
      pathCall("/device/:id/temperature/stream", temperatureStream())
    );
  }
}
//#service-api
