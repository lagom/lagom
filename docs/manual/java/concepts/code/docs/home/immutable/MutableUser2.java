package docs.home.immutable;

import java.util.List;

//#mutable
public class MutableUser2 {
  private final String name;
  private final List<String> phoneNumbers;

  public MutableUser2(String name, List<String> phoneNumbers) {
    this.name = name;
    this.phoneNumbers = phoneNumbers;
  }

  public String getName() {
    return name;
  }

  public List<String> getPhoneNumbers() {
    return phoneNumbers;
  }

}
//#mutable
