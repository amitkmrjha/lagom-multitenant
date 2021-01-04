package com.example.helloworld.impl.projection

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.example.domain.Stock
import com.example.helloworld.impl.HelloWorldEvent
import com.example.helloworld.impl.entity.{PortfolioArchived, PortfolioCreated, PortfolioUpdated, StockArchived, StockCreated, StockUpdated}
import org.slf4j.LoggerFactory

class HelloWorldProjectionHandler(
                                   tag: String,
                                   system: ActorSystem[_]
                                   /*repo: ItemPopularityRepository*/)
  extends Handler[EventEnvelope[HelloWorldEvent]]() {

  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext =
    system.executionContext

  override def process(envelope: EventEnvelope[HelloWorldEvent]): Future[Done] = {
    envelope.event match {
      case StockCreated(stock: Stock) =>
        logHelloWorldEvent(s"Stock created with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        Future.successful(Done)
      case StockUpdated(stock: Stock) =>
        logHelloWorldEvent(s"Stock Updated with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        Future.successful(Done)
      case StockArchived(stock) =>
        logHelloWorldEvent(s"Stock Archived with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        Future.successful(Done)
      case PortfolioCreated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Created with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Future.successful(Done)
      case PortfolioUpdated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Updated with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Future.successful(Done)
      case PortfolioArchived(portfolio) =>
        logHelloWorldEvent(s"Portfolio Archived with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Future.successful(Done)
      case _ =>
        Future.successful(Done)
    }
  }

  private def logHelloWorldEvent(msg: String): Unit = {
      log.info(msg)
  }

}

