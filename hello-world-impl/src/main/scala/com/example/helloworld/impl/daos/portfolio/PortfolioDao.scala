package com.example.helloworld.impl.daos.portfolio

import com.datastax.driver.core.Row
import com.example.domain.{Holding, Portfolio}
import com.example.helloworld.impl.daos.Columns
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession}

import scala.collection.JavaConverters._
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

class PortfolioDao (session: CassandraSession)
                   (implicit ec: ExecutionContext){

  private val logger = Logger(this.getClass)
  def getAll:Future[Seq[Portfolio]] = sessionSelectAll(PortfolioByTenantIdTable.getAllQueryString)

  protected def sessionSelectAll(queryString: String): Future[Seq[Portfolio]] = {
    session.selectAll(queryString).map(_.map(convert))
  }

  protected def sessionSelectOne(queryString: String): Future[Option[Portfolio]] = {
    session.selectOne(queryString).map(_.map(convert))
  }

  protected def convert(r: Row): Portfolio = {
    val ids = Portfolio.getPortfolioIds(tenantId(r))
    Portfolio(ids._1,ids._2,holdings(r))
  }

  private def tenantId(r: Row): String =  r.getString(Columns.PortfolioEntityID)
  private def holdings(r: Row): Seq[Holding] =  r.getSet(Columns.Holdings,classOf[String]).asScala.map(str => Json.parse(str).as[Holding]).toSeq
}