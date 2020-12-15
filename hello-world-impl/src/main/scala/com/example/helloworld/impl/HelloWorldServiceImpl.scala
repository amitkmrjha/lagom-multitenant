package com.example.helloworld.impl

import com.example.helloworld.api
import com.example.helloworld.api.HelloWorldService
import akka.Done
import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import com.example.domain.{Holding, Portfolio, Stock}
import com.example.helloworld.impl.daos.stock.StockDao
import com.lightbend.lagom.scaladsl.api.transport.BadRequest

/**
  * Implementation of the HelloWorldService.
  */
class HelloWorldServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry,
  stockDao:StockDao
)(implicit ec: ExecutionContext)
  extends HelloWorldService {

  /**
    * Looks up the entity for the given ID.
    */
  private def stockEntityRef(id: String): EntityRef[StockCommand] =
    clusterSharding.entityRefFor(StockState.typeKey, id)

  private def portfolioEntityRef(id: String): EntityRef[PortfolioCommand] =
    clusterSharding.entityRefFor(PortfolioState.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def addStock: ServiceCall[Stock, Stock] = ServiceCall { inStock =>
    stockEntityRef(inStock.stockId)
      .ask(reply => AddStock(inStock, reply))
      .map { confirmation =>
        stockConfirmationToResult(inStock.stockId, confirmation)
      }
  }

  override def updateStockPrice(stockId: String, price: Double): ServiceCall[NotUsed, Stock] = ServiceCall { _ =>
    stockEntityRef(stockId)
      .ask(reply => GetStock(reply))
      .map(spo => stockSummaryToResult(stockId, spo))
      .flatMap{ currentStock =>
        stockEntityRef(stockId)
          .ask(reply => UpdateStock(currentStock.copy(price = price), reply))
          .map { confirmation =>
            stockConfirmationToResult(stockId, confirmation)
          }
      }
  }

  override def getStock(stockId: String): ServiceCall[NotUsed, Stock] = ServiceCall { _ =>
    stockEntityRef(stockId)
      .ask(reply => GetStock(reply))
      .map(spo => stockSummaryToResult(stockId, spo))
  }

  override def getAllStock: ServiceCall[NotUsed, Seq[Stock]] = ServiceCall { _ =>
    stockDao.getAll
  }

  override def createPortfolio(tenantId: String): ServiceCall[Seq[Holding], Portfolio] = ServiceCall { input =>
    portfolioEntityRef(tenantId)
      .ask(reply => AddPortfolio(Portfolio(tenantId,input), reply))
      .map { confirmation =>
        portfolioConfirmationToResult(tenantId, confirmation)
      }
  }

  override def updatePortfolio(tenantId: String): ServiceCall[Seq[Holding], Portfolio] = ServiceCall { input =>
    portfolioEntityRef(tenantId)
      .ask(reply => GetPortfolio(reply))
      .map(spo => portfolioSummaryToResult(tenantId, spo))
      .flatMap{ currentStock =>
        portfolioEntityRef(tenantId)
          .ask(reply => UpdatePortfolio(currentStock.copy(holdings = input), reply))
          .map { confirmation =>
            portfolioConfirmationToResult(tenantId, confirmation)
          }
      }
  }

  override def addStockToPortfolio(tenantId: String): ServiceCall[Holding, Portfolio] = ServiceCall { input =>
    portfolioEntityRef(tenantId)
      .ask(reply => GetPortfolio(reply))
      .map(spo => portfolioSummaryToResult(tenantId, spo))
      .flatMap{ currentStock =>
        portfolioEntityRef(tenantId)
          .ask(reply => AddStockToPortfolio(tenantId,input, reply))
          .map { confirmation =>
            portfolioConfirmationToResult(tenantId, confirmation)
          }
      }
  }

  override def removeStockFromPortfolio(tenantId: String): ServiceCall[Holding, Portfolio] = ServiceCall { input =>
    portfolioEntityRef(tenantId)
      .ask(reply => GetPortfolio(reply))
      .map(spo => portfolioSummaryToResult(tenantId, spo))
      .flatMap{ currentStock =>
        portfolioEntityRef(tenantId)
          .ask(reply => RemoveStockFromPortfolio(tenantId,input, reply))
          .map { confirmation =>
            portfolioConfirmationToResult(tenantId, confirmation)
          }
      }
  }

  override def getPortfolio(tenantId: String): ServiceCall[NotUsed, Portfolio] = ServiceCall { _ =>
    portfolioEntityRef(tenantId)
      .ask(reply => GetPortfolio(reply))
      .map(spo => portfolioSummaryToResult(tenantId, spo))
  }

  override def getInvestment(tenantId: String): ServiceCall[NotUsed, Double] = ???

  private def stockConfirmationToResult(id: String, confirmation: StockConfirmation): Stock =
    confirmation match {
      case StockAccepted(summary) => stockSummaryToResult(id, summary)
      case StockRejected(reason)      => throw BadRequest(reason)
    }

  private def stockSummaryToResult(stockId:String, summary: StockSummary):Stock = {
    summary.stockOption.getOrElse(throw BadRequest(s"Stock with id ${stockId} not found."))
  }

  private def portfolioConfirmationToResult(id: String, confirmation: PortfolioConfirmation): Portfolio =
    confirmation match {
      case PortfolioAccepted(summary) => portfolioSummaryToResult(id, summary)
      case PortfolioRejected(reason)      => throw BadRequest(reason)
    }

  private def portfolioSummaryToResult(stockId:String, summary: PortfolioSummary):Portfolio = {
    summary.portfolioOption.getOrElse(throw BadRequest(s"portfolio with id ${stockId} not found."))
  }
}
