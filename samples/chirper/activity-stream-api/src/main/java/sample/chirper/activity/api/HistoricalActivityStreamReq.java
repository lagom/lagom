package sample.chirper.activity.api;

import java.time.Instant;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.google.common.base.MoreObjects;

@Immutable
public final class HistoricalActivityStreamReq {

  public Instant fromTime;

  @JsonCreator
  public HistoricalActivityStreamReq(Instant fromTime) {
	  this.fromTime = fromTime;
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another) return true;
    return another instanceof HistoricalActivityStreamReq
        && equalTo((HistoricalActivityStreamReq) another);
  }

  private boolean equalTo(HistoricalActivityStreamReq another) {
    return fromTime.equals(another.fromTime);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + fromTime.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("HistoricalActivityStreamReq")
        .add("fromTime", fromTime)
        .toString();
  }
}
