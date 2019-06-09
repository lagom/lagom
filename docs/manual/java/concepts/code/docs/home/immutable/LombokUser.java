/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.immutable;

import lombok.Value;

// #lombok-immutable
@Value
public class LombokUser {

  String name;

  String email;
}
// #lombok-immutable
