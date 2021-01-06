package com.example.helloworld.impl.tenant

import akka.Done
import akka.actor.{ActorSystem => ActorSystemClassic}
import com.example.helloworld.impl.daos.portfolio.PortfolioByTenantIdTable
import com.example.helloworld.impl.daos.stock.StockByTenantIdTable

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

trait TenantProjectionComponent {
  def actorSystem: ActorSystemClassic
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  lazy val tenantSession: TenantCassandraSession = new TenantCassandraSession(actorSystem,tenantPlugins)
  Await.ready(tenantSession.executeCreateKeySpace,100.seconds)
  Await.ready(StockByTenantIdTable.createTable()(tenantSession,tenantSession.ec),20.seconds)
  Await.ready(PortfolioByTenantIdTable.createTable()(tenantSession,tenantSession.ec),20.seconds)
}
