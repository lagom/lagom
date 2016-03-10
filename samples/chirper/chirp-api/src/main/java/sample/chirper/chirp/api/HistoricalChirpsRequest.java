package sample.chirper.chirp.api;

import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.pcollections.PSequence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Preconditions;

@Immutable
public final class HistoricalChirpsRequest {

  public final Instant fromTime;
  public final PSequence<String> userIds;

  @JsonCreator
  public HistoricalChirpsRequest(Instant fromTime, PSequence<String> userIds) {
    this.fromTime = Preconditions.checkNotNull(fromTime, "fromTime");
    this.userIds = Preconditions.checkNotNull(userIds, "userIds");
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof HistoricalChirpsRequest && equalTo((HistoricalChirpsRequest) another);
  }

  private boolean equalTo(HistoricalChirpsRequest another) {
    return fromTime.equals(another.fromTime) && userIds.equals(another.userIds);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + fromTime.hashCode();
    h = h * 17 + userIds.hashCode();
    return h;
  }

}
