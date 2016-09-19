package docs.home.persistence;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import org.pcollections.PSequence;

public interface BlogEventProcessorInitial {

  //#processor
  public class BlogEventProcessor extends ReadSideProcessor<BlogEvent> {

    @Override
    public ReadSideProcessor.ReadSideHandler<BlogEvent> buildHandler() {
      // TODO build read side handler
      return null;
    }

    @Override
    public PSequence<AggregateEventTag<BlogEvent>> aggregateTags() {
      // TODO return the tag for the events
      return null;
    }
  }
  //#processor
}
