package com.example.helloworld.api
import com.example.domain.{Portfolio, Stock}
import julienrf.json.derived
import play.api.libs.json._

sealed trait HelloWorldEvent{
  def tenantId:String
}

object HelloWorldEvent {
    implicit val format: OFormat[HelloWorldEvent] =
      derived.flat.oformat((__ \ "type").format[String])

  implicit class EventSerializerOps(event:HelloWorldEvent){
    def se:Array[Byte]  = Json.toBytes(Json.toJson(event))
    //def de:HelloWorldEvent  = Json.toBytes(Json.toJson(event))
  }
}

case class PortfolioCreated(tenantId:String,portfolio:Portfolio) extends HelloWorldEvent
object PortfolioCreated {
  implicit val portfolioCreatedFormat: Format[PortfolioCreated] = Json.format
}

case class PortfolioChanged(tenantId:String,portfolio:Portfolio) extends HelloWorldEvent
object PortfolioChanged {
  implicit val portfolioChangedFormat: Format[PortfolioChanged] = Json.format
}
case class PortfolioRemoved(tenantId:String,portfolio:Portfolio) extends HelloWorldEvent
object PortfolioRemoved {
  implicit val portfolioChangedFormat: Format[PortfolioRemoved] = Json.format
}

case class StockCreated(tenantId:String,stock:Stock) extends HelloWorldEvent
object StockCreated {
  implicit val stockCreatedFormat: Format[StockCreated] = Json.format
}

case class StockChanged(tenantId:String,stock:Stock) extends HelloWorldEvent
object StockChanged {
  implicit val stockChangedFormat: Format[StockChanged] = Json.format
}
case class StockRemoved(tenantId:String,stock:Stock) extends HelloWorldEvent
object StockRemoved {
  implicit val stockChangedFormat: Format[StockRemoved] = Json.format
}