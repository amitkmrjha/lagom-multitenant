package com.example.helloworld.api

import akka.{Done, NotUsed}
import com.example.domain.{Holding, Portfolio, Stock}
import com.lightbend.lagom.scaladsl.api.Service.restCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

object HelloWorldService  {
  val TOPIC_NAME = "hello-world-events"
}



/**
  * The Hello World service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the HelloWorldService.
  */
trait HelloWorldService extends Service {

  def addStock(tenantId:String): ServiceCall[Stock, Stock]
  def updateStockPrice(tenantId:String,stockId: String, price:Double) : ServiceCall[NotUsed, Stock]
  def getStock(tenantId:String,stockId: String): ServiceCall[NotUsed, Stock]
  def getAllStock(tenantId:String): ServiceCall[NotUsed, Seq[Stock]]

  def createPortfolio(tenantId:String,portfolioId:String) : ServiceCall[Seq[Holding], Portfolio]
  def updatePortfolio(tenantId:String,portfolioId:String) : ServiceCall[Seq[Holding], Portfolio]
  def addStockToPortfolio(tenantId:String,portfolioId:String) : ServiceCall[Holding, Portfolio]
  def removeStockFromPortfolio(tenantId:String,portfolioId:String) : ServiceCall[Holding, Portfolio]
  def getPortfolio(tenantId:String,portfolioId:String) : ServiceCall[NotUsed, Portfolio]
  def getInvestment(tenantId:String,portfolioId:String) : ServiceCall[NotUsed, Double]

  /**
    * This gets published to Kafka.
    */
  //def helloWorldTopic(): Topic[HelloWorldEvent]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("hello-world")
      .withCalls(
        restCall(Method.POST, "/api/v1/stock/:tenantId", addStock _),
        restCall(Method.PUT, "/api/v1/stock/:tenantId/:stockId/price/:price", updateStockPrice _),
        restCall(Method.GET, "/api/v1/stock/:tenantId/:stockId", getStock _),
        restCall(Method.GET, "/api/v1/stock/:tenantId", getAllStock _),

        restCall(Method.POST, "/api/v1/portfolio/:tenantId/:portfolioId", createPortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId/:portfolioId", updatePortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId/:portfolioId/add", addStockToPortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId/:portfolioId/remove", removeStockFromPortfolio _),
        restCall(Method.GET, "/api/v1/portfolio/:tenantId/:portfolioId", getPortfolio _),
        restCall(Method.GET, "/api/v1/portfolio/:tenantId/:portfolioId", getInvestment _)
      )/*
      .withTopics(
        topic(HelloWorldService.TOPIC_NAME, helloWorldTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[HelloWorldEvent](_.tenantId)
          )
      )*/
      .withAutoAcl(true)
    // @formatter:on
  }
}
