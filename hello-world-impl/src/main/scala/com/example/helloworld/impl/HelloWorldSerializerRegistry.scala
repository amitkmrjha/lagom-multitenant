package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}

/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object HelloWorldSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[StockAdded],
    JsonSerializer[StockUpdated],
    JsonSerializer[StockArchived],
    JsonSerializer[StockState],
    // the replies use play-json as well
    JsonSerializer[StockSummary],
    JsonSerializer[StockConfirmation],
    JsonSerializer[StockAccepted],
    JsonSerializer[StockRejected],


    JsonSerializer[PortfolioAdded],
    JsonSerializer[PortfolioUpdated],
    JsonSerializer[StockAddedToPortfolio],
    JsonSerializer[StockRemovedFromPortfolio],
    JsonSerializer[PortfolioArchived],
    JsonSerializer[PortfolioState],
    // the replies use play-json as well
    JsonSerializer[PortfolioSummary],
    JsonSerializer[PortfolioConfirmation],
    JsonSerializer[PortfolioAccepted],
    JsonSerializer[PortfolioRejected]
  )
}
