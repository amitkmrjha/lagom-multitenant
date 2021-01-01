package com.example.domain

import play.api.libs.json.{Format, Json, __}
case class Stock(tenantId:Option[String],stockId: String, name: String, price: Double)
object Stock {
  implicit val format: Format[Stock] = Json.format
}
