package com.example.helloworld.impl.daos.stock

import akka.Done
import com.datastax.oss.driver.api.core.cql.{Row, SimpleStatement, Statement}
import com.example.domain.Stock
import com.example.helloworld.impl.daos.Columns
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
class StockDao (tenantSession: TenantCassandraSession)
               (implicit ec: ExecutionContext){

  private val logger = Logger(this.getClass)

  def insert(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    StockByTenantIdTable.insert(stock)(tenantSession,ec)
  }

  def delete(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    StockByTenantIdTable.delete(stock)(tenantSession,ec)
  }

  def getAll()(implicit tenantDataBaseId:TenantPersistenceId):Future[Seq[Stock]] = {
    val p = StockByTenantIdTable.getAllQueryString()(tenantDataBaseId,tenantSession,ec)
    sessionSelectAll(SimpleStatement.newInstance(p))
  }

  protected def sessionSelectAll(stmt: Statement[_])(implicit tenantDataBaseId:TenantPersistenceId): Future[Seq[Stock]] = {
    tenantSession.selectAll(stmt).map(_.map(convert))
  }


  protected def sessionSelectOne(stmt: Statement[_])(implicit tenantDataBaseId:TenantPersistenceId): Future[Option[Stock]] = {
    tenantSession.selectOne(stmt).map(_.map(convert))
  }

  protected def convert(r: Row): Stock = {
    Stock(Option(stockId(r)._1),stockId(r)._2,name(r),price(r))
  }

  private def stockId(r: Row) =  Stock.getStockIds(r.getString(Columns.StockEntityID))
  private def name(r: Row): String =  r.getString(Columns.Name)
  private def price(r: Row): Double =  r.getDouble(Columns.Price)
}
