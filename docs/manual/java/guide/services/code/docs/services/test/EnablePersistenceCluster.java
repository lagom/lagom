package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class EnablePersistenceCluster {
    //#enable-cluster
    private final Setup setup = defaultSetup().withCluster();
    //#enable-cluster
}
