/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.immutable;

// #immutable
import com.lightbend.lagom.javadsl.immutable.ImmutableStyle;
import org.immutables.value.Value;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

@Value.Immutable
@ImmutableStyle
public interface AbstractUser4 {

  String getName();

  @Value.Default
  default PVector<String> getPhoneNumbers() {
    return TreePVector.empty();
  }
}
// #immutable
