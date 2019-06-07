/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.persistence;

import java.util.Optional;

// #post1
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

public class Post1 extends PersistentEntity<BlogCommand, BlogEvent, BlogState> {

  @Override
  public Behavior initialBehavior(Optional<BlogState> snapshotState) {
    BehaviorBuilder b = newBehaviorBuilder(snapshotState.orElse(BlogState.EMPTY));

    // TODO define command and event handlers

    return b.build();
  }
}
// #post1
