package sample.chirper.friend.api;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Immutable
@JsonDeserialize
public final class User {
  public final String userId;
  public final String name;
  public final PSequence<String> friends;

  public User(String userId, String name) {
    this(userId, name, Optional.empty());
  }

  @JsonCreator
  public User(String userId, String name, Optional<PSequence<String>> friends) {
    this.userId = Preconditions.checkNotNull(userId, "userId");
    this.name = Preconditions.checkNotNull(name, "name");
    this.friends = friends.orElse(TreePVector.empty());
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof User && equalTo((User) another);
  }

  private boolean equalTo(User another) {
    return userId.equals(another.userId) && name.equals(another.name) && friends.equals(another.friends);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + userId.hashCode();
    h = h * 17 + name.hashCode();
    h = h * 17 + friends.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("User").add("userId", userId).add("name", name).add("friends", friends)
        .toString();
  }
}
