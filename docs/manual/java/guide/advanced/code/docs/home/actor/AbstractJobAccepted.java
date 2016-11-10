package docs.home.actor;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

//#msg
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = JobAccepted.class)
public interface AbstractJobAccepted extends Jsonable {
  @Value.Parameter
  public String getJobId();
}
//#msg

