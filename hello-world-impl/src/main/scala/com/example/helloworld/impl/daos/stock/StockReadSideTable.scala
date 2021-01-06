package com.example.helloworld.impl.daos.stock

import akka.Done
import com.datastax.driver.core.querybuilder.{QueryBuilder}
import com.example.domain.Stock
import com.example.helloworld.impl.daos.{ColumnFamilies, Columns}
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId}
import play.api.Logger
import play.api.libs.json.Json

import scala.compat.java8.FutureConverters._
import java.util
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

trait StockReadSideTable[T <: Stock] {

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
    implicit val tenantDataBaseId = TenantPersistenceId(t.tenantId.getOrElse(""))
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
    implicit val tenantDataBaseId = TenantPersistenceId(t.tenantId.getOrElse(""))
    val keyspace = session.keySpace()
    val p =  QueryBuilder.delete().from(keyspace,tableName)
      .where(QueryBuilder.eq(Columns.StockEntityID, Stock.getEntityId(t))).toString
    session.executeDDL(p).recover{
      case ex:Exception =>
        println(s"delete had exception ${ex}")
        throw ex
    }
  }
}

object StockByTenantIdTable extends StockReadSideTable[Stock] {

  override protected def tableScript(keySpace:String): String =
    s"""
        CREATE TABLE IF NOT EXISTS $keySpace.$tableName (
          ${Columns.StockEntityID} text,
          ${Columns.Name} text,
          ${Columns.Price} double,
          PRIMARY KEY (${primaryKey})
        )
      """.stripMargin

  override protected def fields: Seq[String] = Seq(
    Columns.StockEntityID,
    Columns.Name,
    Columns.Price
  )

  override protected def cL: util.List[String] = fields.toList.asJava

  override protected def vL: util.List[AnyRef] = fields.map(_ =>
    QueryBuilder.bindMarker().asInstanceOf[AnyRef]).toList.asJava

  override protected def getInsertBindValues(entity: Stock): Seq[AnyRef] = {
    val price = java.lang.Double.valueOf(entity.price)
    val bindValues: Seq[AnyRef] = fields.map(x => x match {
      case Columns.StockEntityID => Stock.getEntityId(entity)
      case Columns.Name => entity.name
      case Columns.Price => price
    })
    bindValues
  }

  override def getAllQueryString()
    (implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String = {
    val keyspace = session.keySpace()
    val select = QueryBuilder.select().from(keyspace,tableName)
    select.toString
  }

  override def getCountQueryString()
    (implicit tenantDataBaseId:TenantPersistenceId,session: TenantCassandraSession, ec: ExecutionContext): String = {
    val keyspace = session.keySpace()
    val countAllQuery = QueryBuilder.select().countAll().from(keyspace,tableName)
    countAllQuery.toString
  }

  override protected def tableName: String = ColumnFamilies.StockById

  override protected def primaryKey: String = s"${Columns.StockEntityID}"

}