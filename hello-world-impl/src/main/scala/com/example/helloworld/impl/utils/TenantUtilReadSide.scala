package com.example.helloworld.impl.utils

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.cassandra.internal.CassandraOffsetStore
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import com.example.helloworld.impl.daos.portfolio.PortfolioByTenantIdTable
import com.example.helloworld.impl.daos.stock.StockByTenantIdTable
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId, TenantPersistencePlugin}

import scala.concurrent.{ExecutionContext, Future}

object TenantUtilReadSide{

  def initTenantSchema(system: ActorSystem[_],tenantCassandraSession:TenantCassandraSession,tenantPlugins: Seq[TenantPersistencePlugin]) = {
    implicit val ec = system.executionContext
    createOffsetKeySpaceIfNotExists(tenantCassandraSession,tenantPlugins)flatMap{_ =>
      createTable(tenantCassandraSession,tenantPlugins)
    }
  }

  private def createOffsetKeySpaceIfNotExists(tenantCassandraSession:TenantCassandraSession,tenantPlugins: Seq[TenantPersistencePlugin])(implicit ec:ExecutionContext): Future[Done] = {
    tenantPlugins.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, p) =>
        acc.flatMap { bs =>
          val keyspace: String = p.projectionPlugin.config.getString("offset-store.keyspace")
          createKeyspace(keyspace,tenantCassandraSession)(p.tenantPersistenceId)map (b => bs :+ b)
        }
    }.map(_ => Done)
  }

  private def createKeyspace(keySpace:String,tenantCassandraSession:TenantCassandraSession)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] = {
    tenantCassandraSession
      .executeDDL(
        s"CREATE KEYSPACE IF NOT EXISTS $keySpace WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor':1 }")
  }

  private def createTable(tenantCassandraSession:TenantCassandraSession,tenantPlugins: Seq[TenantPersistencePlugin])(implicit ec:ExecutionContext): Future[Done] = {
    tenantPlugins.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, p) =>
        acc.flatMap { bs =>
          createTableForTenant(tenantCassandraSession)(p.tenantPersistenceId,ec)map (b => bs :+ b)
        }
    }.map(_ => Done)
  }

  private def createTableForTenant(tenantCassandraSession:TenantCassandraSession)(implicit  tenantPersistenceId:TenantPersistenceId,ec:ExecutionContext): Future[Done] = {
    for{
      d <- PortfolioByTenantIdTable.createTable()(tenantCassandraSession,ec)
      d <- StockByTenantIdTable.createTable()(tenantCassandraSession,ec)
    }yield {
      Done
    }
  }
}
