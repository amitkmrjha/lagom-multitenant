package com.example.domain

import play.api.libs.json.{Format, Json, OFormat, __}

case class Portfolio(tenantId:String, holdings:Seq[Holding]= Seq.empty)
object Portfolio {
  implicit val format: Format[Portfolio] = Json.format
}

case class Holding(stockId:String, units:Int)
object Holding {
 implicit val format: Format[Holding] = Json.format
}

