package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.event.Logging
import akka.persistence.cassandra.session.CassandraSessionSettings
import akka.stream.scaladsl
import com.datastax.driver.core.{BatchStatement, PreparedStatement, Row, Session, Statement}
import com.lightbend.lagom.internal.persistence.cassandra.{CassandraKeyspaceConfig, CassandraReadSideSessionProvider}
import com.typesafe.config.{ConfigFactory, ConfigObject, ConfigValue}

import scala.collection.JavaConverters._
import scala.annotation.varargs
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class TenantCassandraSession(
                              system: ActorSystem,
                              tenantSettings: Seq[TenantCassandraSessionSetting],
                              executionContext: ExecutionContext
                            ) {
  def this(system: ActorSystem) =
    this(
      system,
      tenantSettings = TenantCassandraSessionSetting.toTenantSettings(system),
      executionContext = system.dispatchers.lookup(
        system.settings.config.getString(
          "lagom.persistence.read-side.use-dispatcher"
        )
      )
    )

  implicit val ec :ExecutionContext =  executionContext

  private val log = Logging.getLogger(system, getClass)

  CassandraKeyspaceConfig.validateKeyspace("lagom.persistence.read-side.cassandra", system.settings.config, log)

  /**
    * Internal API
    */
    /*
  private[lagom] val delegate: akka.persistence.cassandra.session.scaladsl.CassandraSession =
    CassandraReadSideSessionProvider(system, settings, executionContext)*/

  private[lagom] val delegateMap: Map[TenantDataBaseId,akka.persistence.cassandra.session.scaladsl.CassandraSession] = {
      tenantSettings.map(s => s.tenantDataBase ->  CassandraReadSideSessionProvider(system, s.setting, executionContext)).toMap
    }

  private def delegate(implicit  tenantDataBase:TenantDataBaseId):akka.persistence.cassandra.session.scaladsl.CassandraSession = {
    delegateMap.get(tenantDataBase) match {
      case Some(s) => s
      case None => throw new Exception(s"No cassandra data base session found for tenant id ${tenantDataBase.tenantId}")
    }
  }

  val getTenants:Seq[TenantDataBaseId] = delegateMap.keys.toSeq

  /**
    * The `Session` of the underlying
    * <a href="http://datastax.github.io/java-driver/">Datastax Java Driver</a>.
    * Can be used in case you need to do something that is not provided by the
    * API exposed by this class. Be careful to not use blocking calls.
    */
  def underlying(implicit  tenantDataBase:TenantDataBaseId): Future[Session] =
    delegate.underlying()


  /**
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useCreateTableTOC.html">Creating a table</a>.
    *
    * The returned `CompletionStage` is completed when the table has been created,
    * or if the statement fails.
    */
  def executeCreateTable(stmt: String): Future[Done] = {

    delegateMap.values.toSeq.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, session) => acc.flatMap(bs => session.executeCreateTable(stmt).map(b => bs :+ b))
    }.map(_ => Done)
  }

  /**
    * Create a `PreparedStatement` that can be bound and used in
    * `executeWrite` or `select` multiple times.
    */
  def prepare(stmt: String): Future[Map[TenantDataBaseId,PreparedStatement]] = {
    delegateMap.foldLeft(Future.successful(Map.empty[TenantDataBaseId,PreparedStatement])) {
      case (acc, (id,session)) => acc.flatMap(bs => session.prepare(stmt).map{b =>
         bs +  (id -> b)
      })
    }
  }

  /**
    * Execute several statements in a batch. First you must [[#prepare]] the
    * statements and bind its parameters.
    *
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useBatchTOC.html">Batching data insertion and updates</a>.
    *
    * The configured write consistency level is used if a specific consistency
    * level has not been set on the `BatchStatement`.
    *
    * The returned `CompletionStage` is completed when the batch has been
    * successfully executed, or if it fails.
    */
  def executeWriteBatch(batch: BatchStatement)(implicit  tenantDataBase:TenantDataBaseId): Future[Done] =
    delegate.executeWriteBatch(batch)

  /**
    * Execute one statement. First you must [[#prepare]] the
    * statement and bind its parameters.
    *
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
    *
    * The configured write consistency level is used if a specific consistency
    * level has not been set on the `Statement`.
    *
    * The returned `CompletionStage` is completed when the statement has been
    * successfully executed, or if it fails.
    */
  def executeWrite(stmt: Statement)(implicit  tenantDataBase:TenantDataBaseId): Future[Done] =
    delegate.executeWrite(stmt)

  /**
    * Prepare, bind and execute one statement in one go.
    *
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useInsertDataTOC.html">Inserting and updating data</a>.
    *
    * The configured write consistency level is used.
    *
    * The returned `CompletionStage` is completed when the statement has been
    * successfully executed, or if it fails.
    */
  @varargs
  def executeWrite(stmt: String, bindValues: AnyRef*)(implicit  tenantDataBase:TenantDataBaseId): Future[Done] =
    delegate.executeWrite(stmt, bindValues: _*)

  /**
    * Execute a select statement. First you must [[#prepare]] the
    * statement and bind its parameters.
    *
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryDataTOC.html">Querying tables</a>.
    *
    * The configured read consistency level is used if a specific consistency
    * level has not been set on the `Statement`.
    *
    * You can return this `Source` as a response in a `ServiceCall`
    * and the elements will be streamed to the client.
    * Otherwise you have to connect a `Sink` that consumes the messages from
    * this `Source` and then `run` the stream.
    */
  def select(stmt: Statement)(implicit  tenantDataBase:TenantDataBaseId): scaladsl.Source[Row, NotUsed] =
    delegate.select(stmt)

  /**
    * Prepare, bind and execute a select statement in one go.
    *
    * See <a href="http://docs.datastax.com/en/cql/3.3/cql/cql_using/useQueryDataTOC.html">Querying tables</a>.
    *
    * The configured read consistency level is used.
    *
    * You can return this `Source` as a response in a `ServiceCall`
    * and the elements will be streamed to the client.
    * Otherwise you have to connect a `Sink` that consumes the messages from
    * this `Source` and then `run` the stream.
    */
  @varargs
  def select(stmt: String, bindValues: AnyRef*)(implicit  tenantDataBase:TenantDataBaseId): scaladsl.Source[Row, NotUsed] =
    delegate.select(stmt, bindValues: _*)

  /**
    * Execute a select statement. First you must [[#prepare]] the statement and
    * bind its parameters. Only use this method when you know that the result
    * is small, e.g. includes a `LIMIT` clause. Otherwise you should use the
    * `select` method that returns a `Source`.
    *
    * The configured read consistency level is used if a specific consistency
    * level has not been set on the `Statement`.
    *
    * The returned `CompletionStage` is completed with the found rows.
    */
  def selectAll(stmt: Statement)(implicit  tenantDataBase:TenantDataBaseId): Future[Seq[Row]] =
    delegate.selectAll(stmt)

  /**
    * Prepare, bind and execute a select statement in one go. Only use this method
    * when you know that the result is small, e.g. includes a `LIMIT` clause.
    * Otherwise you should use the `select` method that returns a `Source`.
    *
    * The configured read consistency level is used.
    *
    * The returned `CompletionStage` is completed with the found rows.
    */
  @varargs
  def selectAll(stmt: String, bindValues: AnyRef*)(implicit  tenantDataBase:TenantDataBaseId): Future[Seq[Row]] =
    delegate.selectAll(stmt, bindValues: _*)

  /**
    * Execute a select statement that returns one row. First you must [[#prepare]] the
    * statement and bind its parameters.
    *
    * The configured read consistency level is used if a specific consistency
    * level has not been set on the `Statement`.
    *
    * The returned `CompletionStage` is completed with the first row,
    * if any.
    */
  def selectOne(stmt: Statement)(implicit  tenantDataBase:TenantDataBaseId): Future[Option[Row]] =
    delegate.selectOne(stmt)

  /**
    * Prepare, bind and execute a select statement that returns one row.
    *
    * The configured read consistency level is used.
    *
    * The returned `CompletionStage` is completed with the first row,
    * if any.
    */
  @varargs
  def selectOne(stmt: String, bindValues: AnyRef*)(implicit  tenantDataBase:TenantDataBaseId): Future[Option[Row]] =
    delegate.selectOne(stmt, bindValues: _*)
}
