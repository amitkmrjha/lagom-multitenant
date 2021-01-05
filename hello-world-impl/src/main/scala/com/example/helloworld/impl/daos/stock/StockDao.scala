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

  def createReadTable():Future[Done] = {
    StockByTenantIdTable.createTable()(session,ec)
  }

  def insert(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    StockByTenantIdTable.insert(stock)(session,ec).flatMap{_ match {
      case Some(b)=>
        session.underlying.map(_.execute(b.boundStatement)).map(_ => Done)
      case None => Future.successful(Done)
    }}
  }

  def delete(stock:Stock)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    StockByTenantIdTable.delete(stock)(session,ec).flatMap{_ match {
      case Some(b)=>
        session.underlying.map(_.execute(b.boundStatement)).map(_ => Done)
      case None => Future.successful(Done)
    }}
  }

  def getAll()(implicit tenantDataBaseId:TenantPersistenceId):Future[Seq[Stock]] = sessionSelectAll(StockByTenantIdTable.getAllQueryString)

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
