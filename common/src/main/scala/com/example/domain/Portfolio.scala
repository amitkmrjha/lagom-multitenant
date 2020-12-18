package com.example.domain

import play.api.libs.json.{Format, Json, OFormat, __}

case class Portfolio(tenantId:String,portfolioId:String, holdings:Seq[Holding]= Seq.empty)
object Portfolio {
  private val delim = '-'
  def getEntityId(portfolio:Portfolio):String = getEntityId(portfolio.tenantId,portfolio.portfolioId)
  def getEntityId(tenantId:String,portfolioId:String) :String= s"${portfolioId}$delim${tenantId}"
  def getPortfolioIds(entityId: String) :(String,String)= {
    val values = entityId.split(delim)
    if(values.size == 2) (values(1),values(0)) else ("NA","NA")
  }
  implicit val format: Format[Portfolio] = Json.format
}

case class Holding(stockId:String, units:Int)
object Holding {
 implicit val format: Format[Holding] = Json.format
}

