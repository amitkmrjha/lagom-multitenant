package com.example.helloworld.impl.daos.portfolio

import akka.Done
import com.datastax.oss.driver.api.core.cql.{Row, SimpleStatement, Statement}
import com.example.domain.{Holding, Portfolio}
import com.example.helloworld.impl.daos.Columns
import com.example.helloworld.impl.daos.stock.StockByTenantIdTable
import com.example.helloworld.impl.tenant.{TenantCassandraSession, TenantPersistenceId}

import scala.collection.JavaConverters._
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class PortfolioDao (session: TenantCassandraSession)
                   (implicit ec: ExecutionContext){

  private val logger = Logger(this.getClass)

  def insert(stock:Portfolio)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    PortfolioByTenantIdTable.insert(stock)(session,ec)
  }

  def delete(stock:Portfolio)(implicit tenantDataBaseId:TenantPersistenceId): Future[Done] = {
    PortfolioByTenantIdTable.insert(stock)(session,ec)
  }

  def getAll()(implicit tenantDataBaseId:TenantPersistenceId):Future[Seq[Portfolio]] = {
    val p = PortfolioByTenantIdTable.getAllQueryString()(tenantDataBaseId,session,ec)
    sessionSelectAll(SimpleStatement.newInstance(p))
  }

  protected def sessionSelectAll(stmt: Statement[_])(implicit tenantDataBaseId:TenantPersistenceId): Future[Seq[Portfolio]] = {
    session.selectAll(stmt).map(_.map(r => convert(r)))
  }

  protected def sessionSelectOne(stmt: Statement[_])(implicit tenantDataBaseId:TenantPersistenceId): Future[Option[Portfolio]] = {
    session.selectOne(stmt).map(_.map(convert))
  }

  protected def convert(r: Row): Portfolio = {
    val ids = Portfolio.getPortfolioIds(tenantId(r))
    Portfolio(ids._1,ids._2,holdings(r))
  }

  private def tenantId(r: Row): String =  r.getString(Columns.PortfolioEntityID)
  private def holdings(r: Row): Seq[Holding] =  r.getSet(Columns.Holdings,classOf[String]).asScala.map(str => Json.parse(str).as[Holding]).toSeq
}