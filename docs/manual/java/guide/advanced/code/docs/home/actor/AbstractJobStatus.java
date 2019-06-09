/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.actor;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

// #msg
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = JobStatus.class)
public interface AbstractJobStatus extends Jsonable {
  @Value.Parameter
  public String getJobId();

  @Value.Parameter
  public String getStatus();
}
// #msg
