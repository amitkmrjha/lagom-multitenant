package com.example.helloworld.impl.daos.portfolio

import akka.Done
import com.datastax.driver.core.{BoundStatement, PreparedStatement}
import com.datastax.driver.core.querybuilder.{Delete, Insert, QueryBuilder, Update}
import com.example.domain.{Holding, Portfolio}
import com.example.helloworld.impl.daos.{ColumnFamilies, Columns}
import com.example.helloworld.impl.daos.stock.ReadSideTable
import com.lightbend.lagom.scaladsl.persistence.cassandra.{TenantBoundStatement, TenantCassandraSession, TenantDataBaseId}
import play.api.Logger
import play.api.libs.json.Json

import java.util
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.JavaConverters._

trait TenantReadSideTable[T <: Portfolio] {

  private val logger = Logger(this.getClass)

  protected val insertPromiseTenant: Promise[Map[TenantDataBaseId,PreparedStatement]] = Promise[Map[TenantDataBaseId,PreparedStatement]]

  protected val deletePromiseTenant: Promise[Map[TenantDataBaseId,PreparedStatement]] = Promise[Map[TenantDataBaseId,PreparedStatement]]

  protected def tableName: String

  protected def primaryKey: String

  protected def tableScript: String

  protected def fields: Seq[String]

  protected def prepareDelete: Delete.Where

  protected def getDeleteBindValues(entity: T): Seq[AnyRef]

  protected def cL: util.List[String]

  protected def vL: util.List[AnyRef]

  protected def prepareInsert: Insert

  protected def getInsertBindValues(entity: T): Seq[AnyRef]

  protected def getAllQueryString: String

  protected def getCountQueryString: String

  def createTable()
                 (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- sessionExecuteCreateTable(tableScript)
    } yield Done
  }

  protected def sessionExecuteCreateTable(tableScript: String)
                                         (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    session.executeCreateTable(tableScript).recover {
      case ex: Exception =>
        logger.error(s"Store MS CreateTable $tableScript execute error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  def prepareStatement()
                      (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    val iFuture = sessionPrepare(prepareInsert.toString)
    insertPromiseTenant.completeWith(iFuture)

    val dFuture = sessionPrepare(prepareDelete.toString)
    insertPromiseTenant.completeWith(dFuture)
    for {
      _ <- iFuture
      _ <- dFuture
    } yield Done
  }

  protected def sessionPrepare(stmt: String)
                              (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Map[TenantDataBaseId,PreparedStatement]] = {
    session.prepare(stmt).recover {
      case ex: Exception =>
        logger.error(s"Statement $stmt prepare error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  protected def bindPrepare(ps: Promise[Map[TenantDataBaseId,PreparedStatement]], bindV: Seq[AnyRef])
                           (implicit tenantDataBaseId: TenantDataBaseId,session: TenantCassandraSession, ec: ExecutionContext): Future[Option[TenantBoundStatement]] = {
    ps.future.map { x =>
      val psOption = x.get(tenantDataBaseId)
      try {
        psOption.map { p =>
          TenantBoundStatement(tenantDataBaseId, p.bind(bindV: _*))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"bindPrepare ${psOption} => ${ex.getMessage}", ex)
          throw ex
      }
    }
  }

  def insert(t: T)
            (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Option[TenantBoundStatement]] = {
    implicit val tenantDataBaseId =  TenantDataBaseId(t.tenantId)
    val bindV = getInsertBindValues(t)
    bindPrepare(insertPromiseTenant, bindV)
  }

  def delete(t: T)
            (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Option[TenantBoundStatement]] = {
    implicit val tenantDataBaseId =  TenantDataBaseId(t.tenantId)
    val bindV = getDeleteBindValues(t)
    bindPrepare(insertPromiseTenant, bindV)
  }
}

object PortfolioByTenantIdTable extends TenantReadSideTable[Portfolio] {
  override protected def tableScript: String =
    s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          ${Columns.TenantId} text,
          ${Columns.Holdings} Set<text>,
          PRIMARY KEY (${primaryKey})
        )
      """.stripMargin

  override protected def fields: Seq[String]  = Seq(
    Columns.TenantId,
    Columns.Holdings
  )

  override protected def cL: util.List[String] = fields.toList.asJava

  override protected def vL: util.List[AnyRef] = fields.map(_ =>
    QueryBuilder.bindMarker().asInstanceOf[AnyRef]).toList.asJava

  override protected def prepareInsert: Insert  = QueryBuilder.insertInto(tableName).values(cL, vL)

  override protected def getInsertBindValues(entity: Portfolio): Seq[AnyRef] = {
    val holdings: util.Set[String] = entity.holdings.map(h => Json.toJson(h).toString()).toSet.asJava
    val bindValues: Seq[AnyRef] = fields.map(x => x match {
      case Columns.TenantId => entity.tenantId
      case Columns.Holdings => holdings
    })
    bindValues
  }

  override val getAllQueryString: String =  {
    val select = QueryBuilder.select().from(tableName)
    select.toString
  }

  override val getCountQueryString: String = {
    val countAllQuery = QueryBuilder.select().countAll().from(tableName)
    countAllQuery.toString
  }

  override protected def tableName: String  = ColumnFamilies.PortfolioById

  override protected def primaryKey: String = s"${Columns.TenantId}"

  override protected def prepareDelete: Delete.Where  = QueryBuilder.delete().from(tableName)
    .where(QueryBuilder.eq(Columns.TenantId, QueryBuilder.bindMarker()))

  override protected def getDeleteBindValues(entity: Portfolio): Seq[AnyRef]  = {
    val bindValues: Seq[AnyRef] = Seq(
      entity.tenantId
    )
    bindValues
  }


}