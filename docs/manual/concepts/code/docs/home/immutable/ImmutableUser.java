package docs.home.immutable;

//#immutable
public class ImmutableUser {
  private final String name;
  private final String email;

  public ImmutableUser(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

}
//#immutable
