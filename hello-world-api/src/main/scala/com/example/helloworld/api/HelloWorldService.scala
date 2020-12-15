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
  val TOPIC_NAME = "greetings"
}

/**
  * The Hello World service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the HelloWorldService.
  */
trait HelloWorldService extends Service {

  def addStock: ServiceCall[Stock, Stock]
  def updateStockPrice(stockId: String, price:Double) : ServiceCall[NotUsed, Stock]
  def getStock(stockId: String): ServiceCall[NotUsed, Stock]
  def getAllStock: ServiceCall[NotUsed, Seq[Stock]]

  def createPortfolio(tenantId:String) : ServiceCall[Seq[Holding], Portfolio]
  def updatePortfolio(tenantId:String) : ServiceCall[Seq[Holding], Portfolio]
  def addStockToPortfolio(tenantId:String) : ServiceCall[Holding, Portfolio]
  def removeStockFromPortfolio(tenantId:String) : ServiceCall[Holding, Portfolio]
  def getPortfolio(tenantId:String) : ServiceCall[NotUsed, Portfolio]
  def getInvestment(tenantId:String) : ServiceCall[NotUsed, Double]

  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("hello-world")
      .withCalls(
        restCall(Method.POST, "/api/v1/stock", addStock _),
        restCall(Method.PUT, "/api/v1/stock/:stockId/price/:price", updateStockPrice _),
        restCall(Method.GET, "/api/v1/stock/:stockId", getStock _),
        restCall(Method.GET, "/api/v1/stock", getAllStock _),

        restCall(Method.POST, "/api/v1/portfolio/:tenantId", createPortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId", updatePortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId/add", addStockToPortfolio _),
        restCall(Method.PUT, "/api/v1/portfolio/:tenantId/remove", removeStockFromPortfolio _),
        restCall(Method.GET, "/api/v1/portfolio/:tenantId", getPortfolio _),
        restCall(Method.GET, "/api/v1/portfolio/:tenantId", getInvestment _)
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}
