/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

import java.util.Optional;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public interface HelloCommand {
  public final class Hello implements HelloCommand, PersistentEntity.ReplyType<String> {
    public final String name;
    public final Optional<String> organization;

    public Hello(String name, Optional<String> organization) {
      this.name = name;
      this.organization = organization;
    }
  }
}
