package sample.chirper.friend.api;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Immutable
@JsonDeserialize
public final class FriendId {

  public final String friendId;

  @JsonCreator
  public FriendId(String friendId) {
    this.friendId = Preconditions.checkNotNull(friendId, "friendId");
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof FriendId && equalTo((FriendId) another);
  }

  private boolean equalTo(FriendId another) {
    return friendId.equals(another.friendId);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + friendId.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("FriendId").add("friendId", friendId).toString();
  }

}
