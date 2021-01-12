package com.example.helloworld.impl.projection.publish

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.scaladsl.SendProducer
import akka.projection.eventsourced.EventEnvelope
import akka.projection.scaladsl.Handler
import com.example.domain.Stock
import com.example.helloworld.impl.HelloWorldEvent
import com.example.helloworld.impl.entity.{PortfolioArchived, PortfolioCreated, PortfolioUpdated, StockArchived, StockCreated, StockUpdated}
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class HelloWorldPublishHandler (system: ActorSystem[_],
                                 topic: String,
                                 sendProducer: SendProducer[String, Array[Byte]])
  extends Handler[EventEnvelope[HelloWorldEvent]] {
  private val log = LoggerFactory.getLogger(getClass)
  private implicit val ec: ExecutionContext =
    system.executionContext

  override def process( envelope: EventEnvelope[HelloWorldEvent]): Future[Done] = {
    val event = envelope.event
    // using the cartId as the key and `DefaultPartitioner` will select partition based on the key
    // so that events for same cart always ends up in same partition
    //val key = event.cartId
    val key = s"HelloWorldPublish"
    val producerRecord = new ProducerRecord(topic, key, serialize(event))
    val result = sendProducer.send(producerRecord).map { recordMetadata =>
      log.info(
        "Published event [{}] to topic/partition {}/{}",
        event,
        topic,
        recordMetadata.partition)
      Done
    }
    result
  }

  private def serialize(event: HelloWorldEvent): Array[Byte] = {
    val protoMessage = event match {
      case StockCreated(stock: Stock) =>
        Array.emptyByteArray
      case StockUpdated(stock: Stock) =>
        logHelloWorldEvent(s"Stock Updated with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        Array.emptyByteArray
      case StockArchived(stock) =>
        logHelloWorldEvent(s"Stock Archived with tenant id ${stock.tenantId} and stock id ${stock.stockId}")
        Array.emptyByteArray
      case PortfolioCreated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Created with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Array.emptyByteArray
      case PortfolioUpdated(portfolio) =>
        logHelloWorldEvent(s"Portfolio Updated with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Array.emptyByteArray
      case PortfolioArchived(portfolio) =>
        logHelloWorldEvent(s"Portfolio Archived with tenant id ${portfolio.tenantId} and stock id ${portfolio.portfolioId}")
        Array.emptyByteArray
    }
    protoMessage
  }

  private def logHelloWorldEvent(msg: String): Unit = {
    log.info(s"Kafka Publish event Projection offset event [ ${msg} ]")
  }
}