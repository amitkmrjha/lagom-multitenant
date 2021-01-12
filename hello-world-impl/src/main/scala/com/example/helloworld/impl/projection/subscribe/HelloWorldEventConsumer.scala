package com.example.helloworld.impl.projection.subscribe

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import akka.Done
import akka.actor.typed.ActorSystem
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.{Committer, Consumer}
import akka.stream.scaladsl.RestartSource
import com.example.helloworld.api.HelloWorldService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import play.api.libs.json.Json

object HelloWorldEventConsumer {
  private val log = LoggerFactory.getLogger(getClass)

  def init(system: ActorSystem[_]): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val topic = HelloWorldService.TOPIC_NAME

    val consumerSettings = ConsumerSettings(
      system,
      new StringDeserializer,
      new ByteArrayDeserializer).withGroupId("shopping-cart-analytics")
    val committerSettings = CommitterSettings(system)

    RestartSource
      .onFailuresWithBackoff(
          minBackoff = 1.second,
          maxBackoff = 30.seconds,
          randomFactor = 0.1) { () =>
        Consumer
          .committableSource(
            consumerSettings,
            Subscriptions.topics(topic)
          )
          .mapAsync(1) { msg =>
            handleRecord(msg.record).map(_ => msg.committableOffset)
          }
          .via(Committer.flow(committerSettings))
      }
      .run()
  }

  private def handleRecord(record: ConsumerRecord[String, Array[Byte]]): Future[Done] = {
    import com.example.helloworld.api.HelloWorldEvent.EventDeSerializerOps
    val event = record.value().de
    log.info(s"       consumed event from Kafka : ${event.tenantId} ${Json.toJson(event)}")
    Future.successful(Done)
  }
}
