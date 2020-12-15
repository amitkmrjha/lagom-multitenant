package com.example.helloworld.impl.daos.stock

import akka.Done
import com.datastax.driver.core.querybuilder.{Delete, Insert, QueryBuilder}
import com.datastax.driver.core.{BoundStatement, PreparedStatement}
import com.example.domain.Stock
import com.example.helloworld.impl.daos.{ColumnFamilies, Columns}
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession}
import play.api.Logger

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future, Promise}

trait ReadSideTable[T <: Stock] {

  private val logger = Logger(this.getClass)

  protected val insertPromise: Promise[PreparedStatement] = Promise[PreparedStatement]

  protected val deletePromise: Promise[PreparedStatement] = Promise[PreparedStatement]

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
                 (implicit session: CassandraSession, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- sessionExecuteCreateTable(tableScript)
    } yield Done
  }

  protected def sessionExecuteCreateTable(tableScript: String)
                                         (implicit session: CassandraSession, ec: ExecutionContext): Future[Done] = {
    session.executeCreateTable(tableScript).recover {
      case ex: Exception =>
        logger.error(s"Store MS CreateTable $tableScript execute error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  def prepareStatement()
                      (implicit session: CassandraSession, ec: ExecutionContext): Future[Done] = {
    val insertRepositoryFuture = sessionPrepare(prepareInsert.toString)
    insertPromise.completeWith(insertRepositoryFuture)
    val deleteRepositoryFuture = sessionPrepare(prepareDelete.toString)
    deletePromise.completeWith(deleteRepositoryFuture)
    for {
      _ <- insertRepositoryFuture
      _ <- deleteRepositoryFuture
    } yield Done
  }

  protected def sessionPrepare(stmt: String)
                              (implicit session: CassandraSession, ec: ExecutionContext): Future[PreparedStatement] = {
    session.prepare(stmt).recover {
      case ex: Exception =>
        logger.error(s"Statement $stmt prepare error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  protected def bindPrepare(ps: Promise[PreparedStatement], bindV: Seq[AnyRef])(implicit session: CassandraSession, ec: ExecutionContext): Future[BoundStatement] = {
    ps.future.map(x =>
      try {
        x.bind(bindV: _*)
      } catch {
        case ex: Exception =>
          logger.error(s"bindPrepare ${x.getQueryString} => ${ex.getMessage}", ex)
          throw ex
      }
    )
  }

  def insert(t: T)
            (implicit session: CassandraSession, ec: ExecutionContext): Future[Option[BoundStatement]] = {
    val bindV = getInsertBindValues(t)
    bindPrepare(insertPromise, bindV).map(x => Some(x))
  }

  def delete(t: T)
            (implicit session: CassandraSession, ec: ExecutionContext): Future[Option[BoundStatement]] = {
    val bindV = getDeleteBindValues(t)
    bindPrepare(deletePromise, bindV).map(x => Some(x))
  }
}
object StockByIdTable extends ReadSideTable[Stock] {
  override protected def tableScript: String =
    s"""
        CREATE TABLE IF NOT EXISTS $tableName (
          ${Columns.StockId} text,
          ${Columns.Name} text,
          ${Columns.Price} double,
          PRIMARY KEY (${primaryKey})
        )
      """.stripMargin

  override protected def fields: Seq[String]  = Seq(
    Columns.StockId,
    Columns.Name,
    Columns.Price
  )

  override protected def cL: util.List[String] = fields.toList.asJava

  override protected def vL: util.List[AnyRef] = fields.map(_ =>
    QueryBuilder.bindMarker().asInstanceOf[AnyRef]).toList.asJava

  override protected def prepareInsert: Insert  = QueryBuilder.insertInto(tableName).values(cL, vL)

  override protected def getInsertBindValues(entity: Stock): Seq[AnyRef] = {
    val price = java.lang.Double.valueOf(entity.price)
    val bindValues: Seq[AnyRef] = fields.map(x => x match {
      case Columns.StockId => entity.stockId
      case Columns.Name => entity.name
      case Columns.Price => price
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

  override protected def tableName: String  = ColumnFamilies.StockById

  override protected def primaryKey: String = s"${Columns.StockId}"

  override protected def prepareDelete: Delete.Where  = QueryBuilder.delete().from(tableName)
    .where(QueryBuilder.eq(Columns.StockId, QueryBuilder.bindMarker()))

  override protected def getDeleteBindValues(entity: Stock): Seq[AnyRef]  = {
    val bindValues: Seq[AnyRef] = Seq(
      entity.stockId
    )
    bindValues
  }
}


