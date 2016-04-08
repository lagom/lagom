/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.persistence.cassandra.CoreCassandraSession

/**
 * Data Access Object for Cassandra. The statements are expressed in
 * <a href="http://docs.datastax.com/en/cql/3.3/cql/cqlIntro.html">Cassandra Query Language</a>
 * (CQL) syntax.
 *
 * The configured keyspace is automatically created if it doesn't already exists. The keyspace
 * is also set as the current keyspace, i.e. it doesn't have to be qualified in the statements.
 *
 * All methods are non-blocking.
 */
abstract class CassandraSession extends CoreCassandraSession
