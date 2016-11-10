package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class DisablePersistence {

  //#setup1
  private final Setup setup1 = defaultSetup().withPersistence(false);
  //#setup1

  //#setup2
  private final Setup setup2 = defaultSetup().withCluster(false);
  //#setup2
}
