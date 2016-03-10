package sample.chirper.chirp.api;

import akka.stream.javadsl.Source;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import static com.lightbend.lagom.javadsl.api.Service.*;

public interface ChirpService extends Service {

  ServiceCall<String, Chirp, NotUsed> addChirp();
  
  ServiceCall<NotUsed, LiveChirpsRequest, Source<Chirp, ?>> getLiveChirps();
  
  ServiceCall<NotUsed, HistoricalChirpsRequest, Source<Chirp, ?>> getHistoricalChirps();

  @Override
  default Descriptor descriptor() {
    // @formatter:off
    return named("chirpservice").with(
        pathCall("/api/chirps/live/:userId", addChirp()),
        pathCall("/api/chirps/live", getLiveChirps()),
        pathCall("/api/chirps/history", getHistoricalChirps())
      ).withAutoAcl(true);
    // @formatter:on
  }
}
