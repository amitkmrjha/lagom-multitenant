package com.example.helloworld.impl

import akka.Done
import com.datastax.driver.core.{BoundStatement, PreparedStatement}
import com.example.domain.{Holding, Portfolio}
import com.example.helloworld.impl.daos.portfolio.PortfolioByTenantIdTable
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, TenantCassandraSession}
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future, Promise}

class PortfolioEventProcessor (session:TenantCassandraSession,readSide:CassandraReadSide)
                              (implicit ec: ExecutionContext) extends ReadSideProcessor[PortfolioEvent] {

  val logger = Logger(this.getClass)

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[PortfolioEvent] = {
    readSide.builder[PortfolioEvent]("portfolio-event-offset")
      .setGlobalPrepare(() => createTables())
      .setPrepare(_ => prepareStatements())
      .setEventHandler[PortfolioAdded](e => portfolioInsert(e.event.portfolio))
      .setEventHandler[PortfolioUpdated](e => portfolioInsert(e.event.portfolio))
      .setEventHandler[StockAddedToPortfolio](e => portfolioAddStock(e.event.tenantId,e.event.holding))
      .setEventHandler[StockRemovedFromPortfolio](e => portfolioRemoveStock(e.event.tenantId,e.event.holding))
      .setEventHandler[PortfolioArchived](e => portfolioDelete(e.event.portfolio))
      .build()
  }

  override def aggregateTags: Set[AggregateEventTag[PortfolioEvent]] = Set(PortfolioEvent.Tag)


  private def createTables() = {
    for {
      _ <- PortfolioByTenantIdTable.createTable()(session, ec)
    } yield Done.getInstance()
  }

  private def portfolioInsert(portfolio: Portfolio) = {
    for {
      irbs <- PortfolioByTenantIdTable.insert(portfolio)(session, ec)//Future[Done]
    } yield List(irbs).flatten
  }

  private def portfolioAddStock(tenantId:String,holding:Holding) = {
    for {
      irbs <- PortfolioByTenantIdTable.updateAdd(tenantId,holding)(session, ec)//Future[Done]
    } yield List(irbs).flatten
  }

  private def portfolioRemoveStock(tenantId:String,holding:Holding) = {
    for {
      irbs <- PortfolioByTenantIdTable.updateDelete(tenantId,holding)(session, ec)//Future[Done]
    } yield List(irbs).flatten
  }

  private def portfolioDelete(portfolio: Portfolio) = {
    for {
      drbs <- PortfolioByTenantIdTable.delete(portfolio)(session, ec)
    } yield List(drbs).flatten
  }

  private def bindPrepare(ps: Promise[PreparedStatement], bindV: Seq[AnyRef]): Future[BoundStatement] = {
    ps.future.map(x =>
      try {
        x.bind(bindV: _*)
      } catch {
        case ex: Exception =>
          logger.error(s"bindPrepare ${x.getQueryString} => ${ex.getMessage}", ex)
          throw ex
      }
    )
  }

  private def prepareStatements() = {
    for {
      _ <- PortfolioByTenantIdTable.prepareStatement()(session, ec)
    } yield {
      Done.getInstance()
    }
  }

  private def sessionExecuteCreateTable(tableScript: String): Future[Done] = {
    session.executeCreateTable(tableScript).recover {
      case ex: Exception =>
        logger.error(s"Portfolio CreateTable $tableScript execute error => ${ex.getMessage}", ex)
        throw ex
    }
  }

  private def sessionPrepare(stmt: String): Future[PreparedStatement] = {
    session.prepare(stmt).recover {
      case ex: Exception =>
        logger.error(s"Statement $stmt prepare error => ${ex.getMessage}", ex)
        throw ex
    }
  }
}