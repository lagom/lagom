/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package sample.helloworld.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public class HelloWorld extends PersistentEntity<HelloCommand, HelloEvent, WorldState> {
    
  @Override
  public Behavior initialBehavior(Optional<WorldState> snapshotState) {
      BehaviorBuilder b = newBehaviorBuilder(
        snapshotState.orElse(new WorldState("Hello", LocalDateTime.now().toString())));

      return b.build();
  }
}
