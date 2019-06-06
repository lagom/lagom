/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

// #compressed-jsonable
import com.lightbend.lagom.serialization.CompressedJsonable;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Author.class)
public interface AbstractAuthor extends CompressedJsonable {

  String getName();

  String biography();
}
// #compressed-jsonable
