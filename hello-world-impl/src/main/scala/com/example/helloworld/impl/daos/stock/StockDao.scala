package com.example.helloworld.impl.daos.stock

import com.datastax.driver.core.Row
import com.example.domain.Stock
import com.example.helloworld.impl.daos.Columns
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
class StockDao (session: CassandraSession)
               (implicit ec: ExecutionContext){

  private val logger = Logger(this.getClass)
  def getAll:Future[Seq[Stock]] = sessionSelectAll(StockByIdTable.getAllQueryString)

   protected def sessionSelectAll(queryString: String): Future[Seq[Stock]] = {
    session.selectAll(queryString).map(_.map(convert))
  }

  protected def sessionSelectOne(queryString: String): Future[Option[Stock]] = {
    session.selectOne(queryString).map(_.map(convert))
  }

  protected def convert(r: Row): Stock = {
    Stock(stockId(r),name(r),price(r))
  }

  private def stockId(r: Row): String =  r.getString(Columns.StockId)
  private def name(r: Row): String =  r.getString(Columns.Name)
  private def price(r: Row): Double =  r.getDouble(Columns.Price)
}
