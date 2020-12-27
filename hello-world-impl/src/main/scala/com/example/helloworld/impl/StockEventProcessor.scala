package com.example.helloworld.impl

import akka.Done
import com.example.domain.Stock
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraReadSide, CassandraSession}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEventTag, ReadSideProcessor}
import com.datastax.driver.core.{BoundStatement, PreparedStatement}
import com.example.helloworld.impl.daos.stock.StockByIdTable

import scala.concurrent.{ExecutionContext, Future, Promise}
import play.api.Logger

class StockEventProcessor(session:CassandraSession,readSide:CassandraReadSide)
                         (implicit ec: ExecutionContext) extends ReadSideProcessor[StockEvent] {

  val logger = Logger(this.getClass)

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[StockEvent] = {
    readSide.builder[StockEvent]("stock-event-offset")
    .setGlobalPrepare(() => createTables())
    .setPrepare(_ => prepareStatements())
    .setEventHandler[StockAdded](e => stockInsert(e.event.stock))
    .setEventHandler[StockUpdated](e => stockInsert(e.event.stock))
    .setEventHandler[StockArchived](e => stockDelete(e.event.stock))
    .build()
  }

  override def aggregateTags: Set[AggregateEventTag[StockEvent]] = Set(StockEvent.Tag)


  private def createTables() = {
    for {
      _ <- StockByIdTable.createTable()(session, ec)
    } yield Done.getInstance()
  }

  private def stockInsert(stock: Stock) = {
    for {
      irbs <- StockByIdTable.insert(stock)(session, ec)//Future[Done]
    } yield List(irbs).flatten
  }

  private def stockDelete(stock: Stock) = {
    for {
      drbs <- StockByIdTable.delete(stock)(session, ec)
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
      _ <- StockByIdTable.prepareStatement()(session, ec)
    } yield {
      Done.getInstance()
    }
  }

  private def sessionExecuteCreateTable(tableScript: String): Future[Done] = {
    session.executeCreateTable(tableScript).recover {
      case ex: Exception =>
        logger.error(s"Stock CreateTable $tableScript execute error => ${ex.getMessage}", ex)
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
