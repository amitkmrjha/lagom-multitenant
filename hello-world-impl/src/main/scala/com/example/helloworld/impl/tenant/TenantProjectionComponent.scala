package com.example.helloworld.impl.tenant

import akka.Done
import akka.actor.{ActorSystem => ActorSystemClassic}
import com.example.helloworld.impl.daos.portfolio.PortfolioByTenantIdTable
import com.example.helloworld.impl.daos.stock.StockByTenantIdTable

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

trait TenantProjectionComponent {
  def actorSystem: ActorSystemClassic
  def executionContext: ExecutionContext
  lazy val tenantPlugins = TenantPersistencePlugin.toTenantPersistencePlugin(actorSystem)
  val session: TenantCassandraSession = new TenantCassandraSession(actorSystem,tenantPlugins)
  Await.ready(session.executeCreateKeySpace,100.seconds)
  Await.ready(StockByTenantIdTable.createTable()(session,executionContext),20.seconds)
  Await.ready(PortfolioByTenantIdTable.createTable()(session,executionContext),20.seconds)
}
