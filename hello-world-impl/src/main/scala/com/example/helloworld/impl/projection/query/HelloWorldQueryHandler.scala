package com.example.helloworld.impl.projection.query

import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.example.domain.Stock
import com.example.helloworld.impl.HelloWorldEvent
import com.example.helloworld.impl.daos.portfolio.PortfolioDao
import com.example.helloworld.impl.daos.stock.StockDao
import com.example.helloworld.impl.entity._
import com.example.helloworld.impl.utils.TenantExtract._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

class HelloWorldQueryHandler(
                                   tag: String,
                                   system: ActorSystem[_],
                                   stockDao: StockDao,
                                   portfolioDao: PortfolioDao)
  extends Handler[EventEnvelope[HelloWorldEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext =
    system.executionContext

  override def process(envelope: EventEnvelope[HelloWorldEvent]): Future[Done] = {
    envelope.event match {
      case StockCreated(stock: Stock) =>
        logHelloWorldEvent(s"Stock created with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        stockDao.insert(stock)(stock.toTenant)
      case StockUpdated(stock: Stock) =>
        logHelloWorldEvent(s"Stock Updated with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        stockDao.insert(stock)(stock.toTenant)
      case StockArchived(stock) =>
        logHelloWorldEvent(s"Stock Archived with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        stockDao.delete(stock)(stock.toTenant)
      case PortfolioCreated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Created with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        portfolioDao.insert(portfolio)(portfolio.toTenant)
      case PortfolioUpdated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Updated with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        portfolioDao.insert(portfolio)(portfolio.toTenant)
      case PortfolioArchived(portfolio) =>
        logHelloWorldEvent(s"Portfolio Archived with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        portfolioDao.delete(portfolio)(portfolio.toTenant)
      case _ =>
        Future.successful(Done)
    }
  }

  private def logHelloWorldEvent(msg: String): Unit = {
    log.info(s"Query Projection offset event [ ${msg} ]")
  }
}

