package sample.chirper.activity.api;

import sample.chirper.chirp.api.Chirp;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface ActivityStreamService extends Service {

  ServiceCall<String, NotUsed, Source<Chirp, ?>> getLiveActivityStream();

  ServiceCall<String, NotUsed, Source<Chirp, ?>> getHistoricalActivityStream();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("activityservice").with(
        pathCall("/api/activity/:userId/live", getLiveActivityStream()),
        pathCall("/api/activity/:userId/history", getHistoricalActivityStream())
      ).withAutoAcl(true);
    // @formatter:on
  }
}
