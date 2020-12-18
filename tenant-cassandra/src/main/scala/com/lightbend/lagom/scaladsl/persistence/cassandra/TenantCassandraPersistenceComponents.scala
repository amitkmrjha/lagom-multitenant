package com.lightbend.lagom.scaladsl.persistence.cassandra

import com.lightbend.lagom.internal.persistence.cassandra.{CassandraOffsetStore, CassandraReadSideSettings}
import com.lightbend.lagom.internal.scaladsl.persistence.cassandra.{ScaladslCassandraOffsetStore, TenantCassandraReadSideImpl}
import com.lightbend.lagom.scaladsl.persistence.{PersistenceComponents, ReadSidePersistenceComponents}
import com.lightbend.lagom.spi.persistence.OffsetStore

trait TenantCassandraPersistenceComponents
  extends PersistenceComponents
    with TenantReadSideCassandraPersistenceComponents
    with WriteSideCassandraPersistenceComponents

trait TenantReadSideCassandraPersistenceComponents extends  ReadSidePersistenceComponents {

  lazy val cassandraSession: CassandraSession                 = new CassandraSession(actorSystem)
  lazy val testCasReadSideSettings: CassandraReadSideSettings = new CassandraReadSideSettings(actorSystem)

  private[lagom] lazy val cassandraOffsetStore: CassandraOffsetStore =
    new ScaladslCassandraOffsetStore(actorSystem, cassandraSession, testCasReadSideSettings, readSideConfig)(
      executionContext
    )
  lazy val offsetStore: OffsetStore = cassandraOffsetStore

  lazy val tenantSession: TenantCassandraSession                 = new TenantCassandraSession(actorSystem)

   lazy val cassandraReadSide: TenantCassandraReadSide =
    new TenantCassandraReadSideImpl(actorSystem, cassandraSession,tenantSession, cassandraOffsetStore)
}