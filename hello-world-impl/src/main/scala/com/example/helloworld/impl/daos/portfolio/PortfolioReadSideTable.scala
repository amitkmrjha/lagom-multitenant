package com.example.helloworld.impl.daos.portfolio

import akka.Done
import com.datastax.driver.core.querybuilder.{QueryBuilder}
import com.example.domain.Portfolio
import com.example.helloworld.impl.daos.{ColumnFamilies, Columns}
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId}
import play.api.Logger
import play.api.libs.json.Json

import java.util
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

trait PortfolioReadSideTable[T <: Portfolio] {

  private val logger = Logger(this.getClass)

  protected def tableName: String

  protected def primaryKey: String

  protected def tableScript(keySpace:String): String

  protected def fields: Seq[String]

  protected def cL: util.List[String]

  protected def vL: util.List[AnyRef]

  protected def getInsertBindValues(entity: T): Seq[AnyRef]

  protected def getAllQueryString()
     (implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String

  protected def getCountQueryString()
     (implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String

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

  def insert(t: T)
            (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    implicit val tenantDataBaseId =  TenantPersistenceId(t.tenantId)
    val keyspace = session.keySpace()
    val bindV = getInsertBindValues(t)
    val p = QueryBuilder.insertInto(keyspace,tableName).values(cL,bindV.asJava).toString
    session.executeDDL(p).recover{
      case ex:Exception =>
        println(s"insert had exception ${ex}")
        throw ex
    }
  }

  def delete(t: T)
            (implicit session: TenantCassandraSession, ec: ExecutionContext): Future[Done] = {
    implicit val tenantDataBaseId =  TenantPersistenceId(t.tenantId)
    val keyspace = session.keySpace()
    val p =  QueryBuilder.delete().from(keyspace,tableName)
      .where(QueryBuilder.eq(Columns.PortfolioEntityID, Portfolio.getEntityId(t))).toString
    session.executeDDL(p).recover{
      case ex:Exception =>
        println(s"delete had exception ${ex}")
        throw ex
    }
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

  override protected def getInsertBindValues(entity: Portfolio): Seq[AnyRef] = {
    val holdings: util.Set[String] = entity.holdings.map(h => Json.toJson(h).toString()).toSet.asJava
    val bindValues: Seq[AnyRef] = fields.map(x => x match {
      case Columns.PortfolioEntityID => Portfolio.getEntityId(entity)
      case Columns.Holdings => holdings
    })
    bindValues
  }

  override def getAllQueryString()(implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String =  {
    val keyspace = session.keySpace()
    val select = QueryBuilder.select().from(keyspace,tableName)
    select.toString
  }

  override def getCountQueryString()(implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String = {
    val keyspace = session.keySpace()
    val countAllQuery = QueryBuilder.select().countAll().from(keyspace,tableName)
    countAllQuery.toString
  }

  override protected def tableName: String  = ColumnFamilies.PortfolioById

  override protected def primaryKey: String = s"${Columns.PortfolioEntityID}"
}
