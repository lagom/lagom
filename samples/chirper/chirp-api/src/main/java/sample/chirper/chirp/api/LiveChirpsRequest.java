package sample.chirper.chirp.api;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.pcollections.PSequence;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Immutable
@JsonDeserialize
public final class LiveChirpsRequest {
  public final PSequence<String> userIds;

  @JsonCreator
  public LiveChirpsRequest(PSequence<String> userIds) {
     this.userIds = Preconditions.checkNotNull(userIds, "userIds");
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another) return true;
    return another instanceof LiveChirpsRequest
        && equalTo((LiveChirpsRequest) another);
  }

  private boolean equalTo(LiveChirpsRequest another) {
    return userIds.equals(another.userIds);
  }


  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + userIds.hashCode();
    return h;
  }


  @Override
  public String toString() {
    return MoreObjects.toStringHelper("LiveChirpsRequest")
        .add("userIds", userIds)
        .toString();
  }
}
