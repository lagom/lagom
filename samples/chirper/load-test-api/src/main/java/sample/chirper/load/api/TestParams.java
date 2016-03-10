package sample.chirper.load.api;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@Immutable
@JsonDeserialize
public final class TestParams {

  public final int users;
  public final int friends;
  public final int chirps;
  public final int clients;
  public final int parallelism;
  public final Optional<String> userIdPrefix;

  public TestParams() {
    this(1000, 10, 100000, 10, 10, Optional.empty());
  }

  @JsonCreator
  public TestParams(int users, int friends, int chirps, int clients, int parallelism, Optional<String> userIdPrefix) {
    this.users = users;
    this.friends = friends;
    this.chirps = chirps;
    this.clients = clients;
    this.parallelism = parallelism;
    this.userIdPrefix = userIdPrefix;
  }

  @Override
  public boolean equals(@Nullable Object another) {
    if (this == another)
      return true;
    return another instanceof TestParams && equalTo((TestParams) another);
  }

  private boolean equalTo(TestParams another) {
    return users == another.users && friends == another.friends && chirps == another.chirps
        && clients == another.clients && parallelism == another.parallelism
        && userIdPrefix.equals(another.userIdPrefix);
  }

  @Override
  public int hashCode() {
    int h = 31;
    h = h * 17 + users;
    h = h * 17 + friends;
    h = h * 17 + chirps;
    h = h * 17 + clients;
    h = h * 17 + parallelism;
    h = h * 17 + userIdPrefix.hashCode();
    return h;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("TestParams").add("users", users).add("friends", friends)
        .add("chirps", chirps).add("clients", clients).add("parallelism", parallelism)
        .add("userIdPrefix", userIdPrefix).toString();
  }
}
