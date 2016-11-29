package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class EnablePersistence {

  //#setup1
  private final Setup setup1 = defaultSetup().withCassandra(true);
  //#setup1

  //#setup2
  private final Setup setup2 = defaultSetup().withCluster(true);
  //#setup2
}
