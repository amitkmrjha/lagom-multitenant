package com.example.domain

import play.api.libs.json.{Format, Json, __}
case class Stock(tenantId:Option[String],stockId: String, name: String, price: Double)
object Stock {
  private val delim = '-'
  def getEntityId(stock:Stock):String = getEntityId(stock.tenantId.getOrElse(""),stock.stockId)
  def getEntityId(tenantId:String,stockId:String) :String= s"${stockId}$delim${tenantId}"
  def getStockIds(entityId: String) :(String,String)= {
    val values = entityId.split(delim)
    if(values.size == 2) (values(1),values(0)) else ("NA","NA")
  }
  implicit val format: Format[Stock] = Json.format

}