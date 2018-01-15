package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class EnablePersistenceCassandra {
    //#enable-cassandra
    private final Setup setup = defaultSetup().withCassandra();
    //#enable-cassandra
}
