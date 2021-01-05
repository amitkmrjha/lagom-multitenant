package com.example.helloworld.impl.daos.portfolio

import akka.Done
import com.datastax.driver.core.querybuilder.{Delete, Insert, QueryBuilder}
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.example.domain.Portfolio
import com.example.helloworld.impl.daos.{ColumnFamilies, Columns}
import com.example.helloworld.impl.tenant.{TenantBoundStatement, TenantCassandraSession, TenantPersistenceId}
import play.api.Logger
import play.api.libs.json.Json

import java.util
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.JavaConverters._

trait PortfolioReadSideTable[T <: Portfolio] {

  private val logger = Logger(this.getClass)

  protected val insertPromiseTenant: Promise[Map[TenantPersistenceId,PreparedStatement]] = Promise[Map[TenantPersistenceId,PreparedStatement]]

  protected val deletePromiseTenant: Promise[Map[TenantPersistenceId,PreparedStatement]] = Promise[Map[TenantPersistenceId,PreparedStatement]]

  protected def tableName: String

  protected def primaryKey: String

  protected def tableScript(keySpace:String): String

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
    session.getTenantKeyspace.foldLeft(Future.successful(Seq.empty[Done])) {
      case (acc, k) => acc.flatMap{bs =>
        sessionExecuteCreateTable(tableScript(k)).map(b => bs :+ b)
      }
    }.map(_ => Done)
  }

  protected def sessionExecuteCreateTable(tableScript: String)
                                         (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    session.executeCreateTable(tableScript).recover {
      case ex: Exception =>
        logger.error(s"Store MS CreateTable $tableScript execute error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  def prepareStatement()(implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    val iFuture = sessionPrepare(prepareInsert.toString)
    insertPromiseTenant.completeWith(iFuture)

    val dFuture = sessionPrepare(prepareDelete.toString)
    deletePromiseTenant.completeWith(dFuture)
    for {
      _ <- iFuture
      _ <- dFuture
    } yield Done
  }

  protected def sessionPrepare(stmt: String)
                              (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Map[TenantPersistenceId,PreparedStatement]] = {
    session.prepare(stmt).recover {
      case ex: Exception =>
        logger.error(s"Statement $stmt prepare error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  protected def bindPrepare(ps: Promise[Map[TenantPersistenceId,PreparedStatement]], bindV: Seq[AnyRef])
                           (implicit tenantDataBaseId: TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): Future[Option[TenantBoundStatement]] = {
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
    implicit val tenantDataBaseId =  TenantPersistenceId(t.tenantId)
    val bindV = getInsertBindValues(t)
    bindPrepare(insertPromiseTenant, bindV)
  }

  def delete(t: T)
            (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Option[TenantBoundStatement]] = {
    implicit val tenantDataBaseId =  TenantPersistenceId(t.tenantId)
    val bindV = getDeleteBindValues(t)
    bindPrepare(deletePromiseTenant, bindV)
  }
}

object PortfolioByTenantIdTable extends PortfolioReadSideTable[Portfolio] {
  override protected def tableScript(keySpace:String): String =
    s"""
        CREATE TABLE IF NOT EXISTS $keySpace.$tableName (
          ${Columns.PortfolioEntityID} text,
          ${Columns.Holdings} Set<text>,
          PRIMARY KEY (${primaryKey})
        )
      """.stripMargin

  override protected def fields: Seq[String]  = Seq(
    Columns.PortfolioEntityID,
    Columns.Holdings
  )

  override protected def cL: util.List[String] = fields.toList.asJava

  override protected def vL: util.List[AnyRef] = fields.map(_ =>
    QueryBuilder.bindMarker().asInstanceOf[AnyRef]).toList.asJava

  override protected def prepareInsert: Insert  = QueryBuilder.insertInto(tableName).values(cL, vL)

  override protected def getInsertBindValues(entity: Portfolio): Seq[AnyRef] = {
    val holdings: util.Set[String] = entity.holdings.map(h => Json.toJson(h).toString()).toSet.asJava
    val bindValues: Seq[AnyRef] = fields.map(x => x match {
      case Columns.PortfolioEntityID => Portfolio.getEntityId(entity)
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

  override protected def primaryKey: String = s"${Columns.PortfolioEntityID}"

  override protected def prepareDelete: Delete.Where  = QueryBuilder.delete().from(tableName)
    .where(QueryBuilder.eq(Columns.PortfolioEntityID, QueryBuilder.bindMarker()))

  override protected def getDeleteBindValues(entity: Portfolio): Seq[AnyRef]  = {
    val bindValues: Seq[AnyRef] = Seq(
      Portfolio.getEntityId(entity)
    )
    bindValues
  }


}
