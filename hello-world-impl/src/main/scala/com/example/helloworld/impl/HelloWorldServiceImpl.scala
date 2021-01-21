package com.example.helloworld.impl

import com.example.helloworld.api
import com.example.helloworld.api.HelloWorldService
import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRef, PersistentEntityRegistry}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import com.example.domain.{Holding, Portfolio, Stock}
import com.example.helloworld.impl.daos.portfolio.PortfolioDao
import com.example.helloworld.impl.daos.stock.StockDao
import com.example.helloworld.impl.entity.{CreatePortfolio, CreateStock, GetPortfolio, GetStock, PortfolioCommand, PortfolioEntity, StockCommand, StockEntity, UpdatePortfolio, UpdateStock}
import com.example.helloworld.impl.tenant.{TenantPersistenceId, TenantPersistentEntityRegistry}
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import play.api.Logger
import com.example.helloworld.impl.utils.FutureConverter.FutureOptionOps
/**
  * Implementation of the HelloWorldService.
  */
class HelloWorldServiceImpl(
                             tenantPersistentEntityRegistry: TenantPersistentEntityRegistry,
                             stockDao: StockDao,
                             portfolioDao: PortfolioDao
)(implicit ec: ExecutionContext)
  extends HelloWorldService {

  private val logger = Logger(this.getClass)

  /**
    * Looks up the entity for the given ID.
    */
  private def stockEntityRef(id: String)(implicit tenantPersistenceId: TenantPersistenceId): PersistentEntityRef[StockCommand] =
    tenantPersistentEntityRegistry.refFor[StockEntity](id)

  private def portfolioEntityRef(portfolioId:String)(implicit tenantPersistenceId: TenantPersistenceId): PersistentEntityRef[PortfolioCommand] =
    tenantPersistentEntityRegistry.refFor[PortfolioEntity](portfolioId)

  implicit val timeout = Timeout(30.seconds)

  override def addStock(tenantId:String): ServiceCall[Stock, Stock] = ServiceCall { input =>
    val inStock = input.copy(tenantId = Option(tenantId))
    stockEntityRef(inStock.stockId)(TenantPersistenceId(tenantId))
      .ask(CreateStock(inStock))
      .map { confirmation => inStock}.recover {
      case ex: Exception =>
        logger.error("addStock error : ",ex)
        throw ex
    }
  }

  override def updateStockPrice(tenantId:String,stockId: String, price: Double): ServiceCall[NotUsed, Stock] = ServiceCall { _ =>
    stockEntityRef(stockId)(TenantPersistenceId(tenantId))
      .ask(GetStock)
      .toFutureT(s"Stock with tenant id ${tenantId}, stock id ${stockId}  not found.")
      .flatMap{ currentStock =>
        stockEntityRef(stockId)(TenantPersistenceId(tenantId))
          .ask(UpdateStock(currentStock.copy(price = price)))
          .map(_ => currentStock.copy(price = price))
      }.recover {
      case ex: Exception =>
        logger.error("updateStockPrice error : ",ex)
        throw ex
    }
  }

  override def getStock(tenantId:String,stockId: String): ServiceCall[NotUsed, Stock] =  ServiceCall { _ =>
    stockEntityRef(stockId)(TenantPersistenceId(tenantId))
      .ask(GetStock)
      .toFutureT(s"Stock with tenant id ${tenantId}, stock id ${stockId}  not found.")
      .recover {
      case ex: Exception =>
        logger.error("getStock error : ",ex)
        throw ex
    }
  }

  override def getAllStock(tenantId:String): ServiceCall[NotUsed, Seq[Stock]] = ServiceCall { _ =>
    stockDao.getAll()(TenantPersistenceId(tenantId))
  }

  override def createPortfolio(tenantId:String,portfolioId:String): ServiceCall[Seq[Holding], Portfolio] = ServiceCall { input =>
    portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
      .ask(CreatePortfolio(Portfolio(tenantId,portfolioId,input)))
      .map ( confirmation =>Portfolio(tenantId,portfolioId,input))
      .recover {
        case ex: Exception =>
          logger.error("createPortfolio error : ",ex)
          throw ex
      }
  }

  override def updatePortfolio(tenantId:String,portfolioId:String): ServiceCall[Seq[Holding], Portfolio] = ServiceCall { input =>
    portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
      .ask(GetPortfolio)
      .toFutureT(s"Portfolio with tenant id ${tenantId}, portfolio id ${portfolioId}  not found.")
      .flatMap{ currentStock =>
        portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
        .ask(UpdatePortfolio(currentStock.copy(holdings = input)))
        .map(_ => currentStock.copy(holdings = input))
      }
  }


  override def addStockToPortfolio(tenantId:String,portfolioId:String): ServiceCall[Holding, Portfolio] = ServiceCall { input =>
    portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
      .ask(GetPortfolio)
      .toFutureT(s"Portfolio with tenant id ${tenantId}, portfolio id ${portfolioId}  not found.")
      .flatMap{ currentPortfolio =>
        val currentHoldings = currentPortfolio.holdings
        val newHoldings = currentHoldings.filter(e => e.stockId!=input.stockId):+ input
        val newPortfolio = currentPortfolio.copy(holdings = newHoldings)
        portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
          .ask(UpdatePortfolio(newPortfolio))
          .map(_ => newPortfolio)
      }
  }

  override def removeStockFromPortfolio(tenantId:String,portfolioId:String): ServiceCall[Holding, Portfolio] = ServiceCall { input =>
    portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
      .ask(GetPortfolio)
      .toFutureT(s"Portfolio with tenant id ${tenantId}, portfolio id ${portfolioId}  not found.")
      .flatMap{ currentPortfolio =>
        val newPortfolio = currentPortfolio.copy(holdings = currentPortfolio.holdings.filter(e => e!=input) )
        portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
          .ask(UpdatePortfolio(newPortfolio))
          .map(_ => newPortfolio)
      }
  }

  override def getPortfolio(tenantId:String,portfolioId:String): ServiceCall[NotUsed, Portfolio] = ServiceCall { _ =>
    portfolioEntityRef(portfolioId)(TenantPersistenceId(tenantId))
      .ask(GetPortfolio)
      .toFutureT(s"Portfolio with tenant id ${tenantId}, portfolio id ${portfolioId}  not found.")
  }

  override def getInvestment(tenantId:String,portfolioId:String): ServiceCall[NotUsed, Double] = ???

  override def getAllPortFolio(tenantId: String): ServiceCall[NotUsed, Seq[Portfolio]] =  ServiceCall { _ =>
    portfolioDao.getAll()(TenantPersistenceId(tenantId))
  }
}
