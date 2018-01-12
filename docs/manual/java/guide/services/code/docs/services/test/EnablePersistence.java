package docs.services.test;

import static com.lightbend.lagom.javadsl.testkit.ServiceTest.defaultSetup;

import com.lightbend.lagom.javadsl.testkit.ServiceTest.Setup;

@SuppressWarnings("unused")
public class EnablePersistenceCassandra {
    //#enable-cassandra
    private final Setup setup = defaultSetup().withCassandra();
    //#enable-cassandra
}

@SuppressWarnings("unused")
public class EnablePersistenceJdbc {
    //#enable-jdbc
    private final Setup setup = defaultSetup().withJdbc();
    //#enable-jdbc
}

@SuppressWarnings("unused")
public class EnablePersistenceCluster {
    //#enable-cluster
    private final Setup setup = defaultSetup().withCluster();
    //#enable-cluster
}
