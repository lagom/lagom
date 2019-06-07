/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization.v2a;

import java.util.Optional;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

// #add-optional
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ItemAdded.class)
public interface AbstractItemAdded extends Jsonable {

  String getShoppingCartId();

  String getProductId();

  int getQuantity();

  Optional<Double> getDiscount();

  @Value.Default
  default String getNote() {
    return "";
  }
}
// #add-optional
