
package impl;

import api.Hello;
import api.HelloCommand;
import java.time.LocalDateTime;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import java.util.Optional;

public class HelloWorld extends PersistentEntity<HelloCommand, Void, WorldState> {

  @Override
  public Behavior initialBehavior(Optional<WorldState> snapshotState) {

    BehaviorBuilder b = newBehaviorBuilder(snapshotState.orElse(
        WorldState.builder().message("Hello").timestamp(LocalDateTime.now()).build()));

    b.setReadOnlyCommandHandler(Hello.class,
        (cmd, ctx) -> ctx.reply(state().getMessage() + ", " + cmd.getName() + "!"));

    return b.build();
  }

}
