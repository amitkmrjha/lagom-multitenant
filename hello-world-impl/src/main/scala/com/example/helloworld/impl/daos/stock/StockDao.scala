package com.example.helloworld.impl.daos.stock

import akka.Done
import com.datastax.oss.driver.api.core.cql.{Row}
import com.example.domain.Stock
import com.example.helloworld.impl.daos.Columns
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
class StockDao (session: TenantCassandraSession)
               (implicit ec: ExecutionContext){

  private val logger = Logger(this.getClass)

  def insert(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = ???

  def delete(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = ???

  def getAll()(implicit tenantDataBaseId:TenantPersistenceId):Future[Seq[Stock]] = ???

   protected def sessionSelectAll(queryString: String)(implicit tenantDataBaseId:TenantPersistenceId): Future[Seq[Stock]] = {
    session.selectAll(queryString).map(_.map(convert))
  }

  protected def sessionSelectOne(queryString: String)(implicit tenantDataBaseId:TenantPersistenceId): Future[Option[Stock]] = {
    session.selectOne(queryString).map(_.map(convert))
  }

  protected def convert(r: Row): Stock = {
    Stock(tenantId(r),stockId(r),name(r),price(r))
  }
  private def tenantId(r: Row): Option[String] =  Option(r.getString(Columns.TenantId))
  private def stockId(r: Row): String =  r.getString(Columns.StockEntityID)
  private def name(r: Row): String =  r.getString(Columns.Name)
  private def price(r: Row): Double =  r.getDouble(Columns.Price)
}
