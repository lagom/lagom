/*
 * Copyright (C) Lightbend Inc. <https://www.lightbend.com>
 */

package docs.javadsl.gettingstarted.helloservice;

import akka.cluster.sharding.typed.javadsl.EntityTypeKey;

public class HelloAggregate {

  public static EntityTypeKey<HelloCommand> ENTITY_TYPE_KEY =
      EntityTypeKey.create(HelloCommand.class, "HelloAggregate");
}
