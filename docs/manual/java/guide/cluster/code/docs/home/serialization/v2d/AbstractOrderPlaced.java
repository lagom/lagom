/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization.v2d;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

// #rename-class
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = OrderPlaced.class)
public interface AbstractOrderPlaced extends Jsonable {
  String getShoppingCartId();
}
// #rename-class
