package com.example.helloworld.impl.tenant

import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraPersistenceComponents, WriteSideCassandraPersistenceComponents}
import akka.actor.{ActorSystem => ActorSystemClassic}
import com.example.helloworld.impl.daos.portfolio.PortfolioByTenantIdTable
import com.example.helloworld.impl.daos.stock.StockByTenantIdTable

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

trait TenantPersistenceComponent extends /*CassandraPersistenceComponents*/ WriteSideCassandraPersistenceComponents {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins: Seq[TenantPersistencePlugin] = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val  tenantPersistentEntityRegistry: TenantPersistentEntityRegistry = new TenantPersistentEntityRegistry(actorSystem,tenantPlugins)
  lazy val tenantSession: TenantCassandraSession = new TenantCassandraSession(actorSystem,tenantPlugins)
  Await.ready(tenantSession.executeCreateKeySpace,100.seconds)
  Await.ready(StockByTenantIdTable.createTable()(tenantSession,tenantSession.ec),20.seconds)
  Await.ready(PortfolioByTenantIdTable.createTable()(tenantSession,tenantSession.ec),20.seconds)
}
