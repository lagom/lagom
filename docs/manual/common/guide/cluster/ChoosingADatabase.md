# Choosing a database

Lagom is compatible with the following databases:

* [Cassandra](https://cassandra.apache.org/)
* [PostgreSQL](https://www.postgresql.org/)
* [MySQL](https://www.mysql.com/)
* [Oracle](https://www.oracle.com/database/index.html)
* [H2](https://www.h2database.com/)
* [Microsoft SQL Server](https://www.microsoft.com/en-us/sql-server/)
* [Couchbase](https://www.couchbase.com/)

For instructions on configuring your project to use Cassandra, see [[Using Cassandra for Persistence|PersistentEntityCassandra]]. If instead you want to use one of the relational databases listed above, see [[Using a Relational Database for Persistence|PersistentEntityRDBMS]] on how to configure your project. If you wish to use Couchbase, proceed to the [Lagom section of the plugin site](https://doc.akka.io/docs/akka-persistence-couchbase/current/lagom-persistent-entity.html) for all the details.

To see how to combine Cassandra for write-side persistence and JDBC for a read-side view, see the [Mixed Persistence Service](https://github.com/lagom/lagom-samples/blob/1.6.x/mixed-persistence/) examples.

Lagom provides out of the box support for running Cassandra in a development environment - developers do not need to install, configure or manage Cassandra at all themselves when using Lagom, which makes for great developer velocity, and it means gone are the days where developers spend days setting up their development environment before they can start to be productive on a project.
