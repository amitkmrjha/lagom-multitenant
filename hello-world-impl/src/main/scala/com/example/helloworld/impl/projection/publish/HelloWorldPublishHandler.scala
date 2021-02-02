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
    val se = serialize(event)
    val producerRecord = new ProducerRecord(topic, se._1, se._2)
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

  private def serialize(event: HelloWorldEvent):(String,Array[Byte])  = {
    import com.example.helloworld.impl.HelloWorldEvent.HelloWorldEventOps
    import com.example.helloworld.api.HelloWorldEvent.EventSerializerOps
    val tenantId = event.toApiEvent.tenantId
    val binary = event.toApiEvent.se
    (tenantId, binary)
  }
}