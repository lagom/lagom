/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.actor;

import docs.home.actor.Job;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

// #msg
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Job.class)
public interface AbstractJob extends Jsonable {
  @Value.Parameter
  public String getJobId();

  @Value.Parameter
  public String getTask();

  @Value.Parameter
  public String getPayload();
}
// #msg
