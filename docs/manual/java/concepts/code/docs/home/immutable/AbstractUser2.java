/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.immutable;

import java.util.List;

import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;

// #immutable
@Value.Immutable
@ImmutableStyle
public interface AbstractUser2 {

  String getName();

  List<String> getPhoneNumbers();
}
// #immutable
