package com.example.helloworld.api
import com.example.domain.Portfolio
import julienrf.json.derived
import play.api.libs.json._

sealed trait HelloWorldEvent{
  def tenantId:String
}

object HelloWorldEvent {
    implicit val format: OFormat[HelloWorldEvent] =
      derived.flat.oformat((__ \ "type").format[String])
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