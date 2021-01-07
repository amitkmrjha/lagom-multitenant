package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import akka.actor.typed.scaladsl.adapter._

import scala.collection.immutable
import scala.concurrent.{Await, ExecutionContext, Future}

class TenantCassandraSession(system: ActorSystem,tenantPlugins: Seq[TenantPersistencePlugin]) {

  implicit val ec: ExecutionContext =system.toTyped.executionContext

  def delegate(implicit tenantId:TenantPersistenceId):CassandraSession = {
    tenantPlugins.find(_.tenantPersistenceId.tenantId == tenantId.tenantId) match {
      case Some(s) => CassandraSessionRegistry(system).sessionFor(s"${s.projectionPlugin.pluginId}.session-config")
      case None => throw new Exception(s"No akka projection config found for tenant id ${tenantId.tenantId}")
    }
  }

  def underlying()(implicit  tenantPersistenceId:TenantPersistenceId): Future[CqlSession] =delegate.underlying()

  def close(executionContext: ExecutionContext): Future[Done] = {
    implicit val ec: ExecutionContext = executionContext
    val rs = tenantPlugins.map{t =>
      delegate(t.tenantPersistenceId).close(ec)
    }
    Future.sequence(rs).map(_ => Done)
  }


  def executeDDL(stmt: String)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] = {
    delegate.executeDDL(stmt)
  }

  def executeCreateKeySpace: Future[Done] = {
    tenantPlugins.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, p) =>
        acc.flatMap { bs =>
          val keyspace: String = p.projectionPlugin.keyspace
          val stmt = s"CREATE KEYSPACE IF NOT EXISTS $keyspace WITH REPLICATION = { 'class' : 'SimpleStrategy','replication_factor':1 }"
          delegate(p.tenantPersistenceId).executeDDL(stmt).map (b => bs :+ b)
        }
    }.map(_ =>Done)
  }

  def executeCreateTable(stmt: String): Future[Done] = {
    tenantPlugins.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, p) => acc.flatMap{bs =>
        delegate(p.tenantPersistenceId).executeDDL(stmt) .map(b => bs :+ b)
      }
    }.map(_ => Done)
  }

  def prepare(stmt: String): Future[Map[TenantPersistenceId,PreparedStatement]] = {
    tenantPlugins.foldLeft(Future.successful(Map.empty[TenantPersistenceId,PreparedStatement])) {
      case (acc, p) => acc.flatMap{bs =>
        val session = CassandraSessionRegistry(system).sessionFor(p.projectionPlugin.pluginId)
        session.prepare(stmt).map{b =>
          bs +  (p.tenantPersistenceId -> b)
        }}
    }
  }

  def executeWriteBatch(batch: BatchStatement)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] =
    executeWrite(batch)

  def executeWrite(stmt: Statement[_])(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] = {
    delegate.executeWrite(stmt)
  }

  def executeWrite(stmt: String, bindValues: AnyRef*)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] = {
    delegate.executeWrite(stmt, bindValues)
  }

  def select(stmt: Statement[_])(implicit  tenantPersistenceId:TenantPersistenceId): Source[Row, NotUsed] = {
    delegate.select(stmt)
  }
  def select(stmt: Future[Statement[_]])(implicit  tenantPersistenceId:TenantPersistenceId): Source[Row, NotUsed] = {
    delegate.select(stmt)
  }

  def select(stmt: String, bindValues: AnyRef*)(implicit  tenantPersistenceId:TenantPersistenceId): Source[Row, NotUsed] = {
    delegate.select(stmt, bindValues)
  }

  def selectAll(stmt: Statement[_])(implicit  tenantPersistenceId:TenantPersistenceId): Future[immutable.Seq[Row]] = {
    delegate.selectAll(stmt)
  }
  def selectAll(stmt: String, bindValues: AnyRef*)(implicit  tenantPersistenceId:TenantPersistenceId): Future[immutable.Seq[Row]] = {
    delegate.selectAll(stmt,bindValues)
  }
  def selectOne(stmt: Statement[_])(implicit  tenantPersistenceId:TenantPersistenceId): Future[Option[Row]] = {
    delegate.selectOne(stmt)
  }


  def selectOne(stmt: String, bindValues: AnyRef*)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Option[Row]] = {
    delegate.selectOne(stmt,bindValues)
  }

  def getTenantKeyspace:Seq[String] = {
    tenantPlugins.map(p => p.projectionPlugin.keyspace)
  }

  def keySpace()(implicit  tenantPersistenceId:TenantPersistenceId) =
    tenantPlugins.find(_.projectionPlugin.tenantPersistenceId.tenantId == tenantPersistenceId.tenantId) match {
      case Some(s) =>  s.projectionPlugin.keyspace
      case None =>
        throw new Exception(s"No keyspace found for tenant id ${tenantPersistenceId.tenantId}")
    }
}
