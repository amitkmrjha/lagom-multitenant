package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.internal.persistence.cassandra.{CassandraOffsetStore, CassandraReadSideSettings}
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ScaladslCassandraOffsetStore, TenantCassandraReadSideImpl}
import com.lightbend.lagom.scaladsl.persistence.{PersistenceComponents, ReadSidePersistenceComponents}
import com.lightbend.lagom.spi.persistence.OffsetStore

trait TenantCassandraPersistenceComponents
  extends PersistenceComponents
    with TenantReadSideCassandraPersistenceComponents
    with WriteSideCassandraPersistenceComponents

trait TenantReadSideCassandraPersistenceComponents extends ReadSideCassandraPersistenceComponents {
  lazy val tenantSession: TenantCassandraSession                 = new TenantCassandraSession(actorSystem)
  override lazy val cassandraReadSide: CassandraReadSide =
    new TenantCassandraReadSideImpl(actorSystem, cassandraSession,tenantSession, cassandraOffsetStore)
}