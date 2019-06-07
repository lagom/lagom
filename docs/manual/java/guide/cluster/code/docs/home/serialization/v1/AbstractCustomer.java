/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.serialization.v1;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import com.lightbend.lagom.serialization.Jsonable;
import org.immutables.value.Value;

// #structural
@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = Customer.class)
public interface AbstractCustomer extends Jsonable {
  String getName();

  String getStreet();

  String getCity();

  String getZipCode();

  String getCountry();
}
// #structural
