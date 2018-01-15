package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class EnablePersistenceJdbc {
    //#enable-jdbc
    private final Setup setup = defaultSetup().withJdbc();
    //#enable-jdbc
}
