/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.immutable;

import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

// #immutable
@Value.Immutable
@ImmutableStyle
public interface AbstractUser {

  String getName();

  String getEmail();
}
// #immutable
