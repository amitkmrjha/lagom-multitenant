package com.example.helloworld.impl

import com.example.domain.Stock
import com.example.helloworld.impl.entity.{PortfolioArchived, PortfolioCreated, PortfolioUpdated, StockArchived, StockCreated, StockUpdated}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}
import  com.example.helloworld.api.HelloWorldEvent.EventSerializerOps
trait HelloWorldEvent extends AggregateEvent[HelloWorldEvent] {
  override def aggregateTag: AggregateEventTagger[HelloWorldEvent] = HelloWorldEvent.Tag
}

object HelloWorldEvent {
  val NumShards = 1
  val Tag: AggregateEventShards[HelloWorldEvent] = AggregateEventTag.sharded[HelloWorldEvent](NumShards)

  implicit class HelloWorldEventOps(event:HelloWorldEvent){
    def toApiEvent:com.example.helloworld.api.HelloWorldEvent = event match {
      case StockCreated(stock: Stock) =>
        com.example.helloworld.api.StockCreated(stock.tenantId.getOrElse("TenantID-NA"),stock)
      case StockUpdated(stock: Stock) =>
        com.example.helloworld.api.StockChanged(stock.tenantId.getOrElse("TenantID-NA"),stock)
      case StockArchived(stock) =>
        com.example.helloworld.api.StockRemoved(stock.tenantId.getOrElse("TenantID-NA"),stock)
      case PortfolioCreated(portfolio) =>
        com.example.helloworld.api.PortfolioCreated(portfolio.tenantId,portfolio)
      case PortfolioUpdated(portfolio) =>
        com.example.helloworld.api.PortfolioChanged(portfolio.tenantId,portfolio)
      case PortfolioArchived(portfolio) =>
        com.example.helloworld.api.PortfolioRemoved(portfolio.tenantId,portfolio)
    }
  }
}