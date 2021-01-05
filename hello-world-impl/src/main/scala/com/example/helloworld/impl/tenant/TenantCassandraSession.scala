package com.example.helloworld.impl.tenant

import akka.actor.ActorSystem
import akka.stream.alpakka.cassandra.scaladsl.{CassandraSession, CassandraSessionRegistry}
import akka.stream.scaladsl.{Source}
import akka.{Done, NotUsed}
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql._
import akka.actor.typed.scaladsl.adapter._
import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

 class TenantCassandraSession(system: ActorSystem,tenantPlugins: Seq[TenantPersistencePlugin]) {
   implicit val ec: ExecutionContext =system.toTyped.executionContext

  private lazy  val pluginsKeyMap = tenantPlugins.map{ t =>
    t.tenantPersistenceId -> s"tenant.akka-projection-cassandra-plugin.${t.tenantPersistenceId.tenantId}"
  }.toMap

   def delegate(implicit tenantId:TenantPersistenceId):CassandraSession = {
     pluginsKeyMap.get(tenantId) match {
       case Some(s) => CassandraSessionRegistry(system).sessionFor(s)
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


   def executeDDL(stmt: String)(implicit  tenantPersistenceId:TenantPersistenceId): Future[Done] =
     delegate.executeDDL(stmt)


   @deprecated("Use executeDDL instead.", "0.100")
   def executeCreateTable(stmt: String): Future[Done] = {
     pluginsKeyMap.values.toSeq.foldLeft(Future.successful(Seq.empty[Done])) {
       case (acc, pluginKey) => acc.flatMap{bs =>
         val session = CassandraSessionRegistry(system).sessionFor(pluginKey)
         session.executeCreateTable(stmt).map(b => bs :+ b)}
     }.map(_ => Done)
   }

   def prepare(stmt: String): Future[Map[TenantPersistenceId,PreparedStatement]] = {
     pluginsKeyMap.foldLeft(Future.successful(Map.empty[TenantPersistenceId,PreparedStatement])) {
       case (acc, (id,pluginKey)) => acc.flatMap{bs =>
         val session = CassandraSessionRegistry(system).sessionFor(pluginKey)
         session.prepare(stmt).map{b =>
         bs +  (id -> b)
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
}
