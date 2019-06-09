/*
 * Copyright (C) 2016-2019 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.home.immutable;

import java.util.Collections;

import java.util.ArrayList;
import java.util.List;

// #immutable
public class ImmutableUser2 {
  private final String name;
  private final List<String> phoneNumbers;

  public ImmutableUser2(String name, List<String> phoneNumbers) {
    this.name = name;
    this.phoneNumbers = new ArrayList<>(phoneNumbers);
  }

  public String getName() {
    return name;
  }

  public List<String> getPhoneNumbers() {
    return Collections.unmodifiableList(phoneNumbers);
  }
}
// #immutable
