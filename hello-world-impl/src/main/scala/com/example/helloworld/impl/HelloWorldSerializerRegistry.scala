package com.example.helloworld.impl

import com.example.domain.{Portfolio, Stock}
import com.example.helloworld.impl.entity.{PortfolioArchived, PortfolioCreated, PortfolioUpdated, StockArchived, StockCreated, StockUpdated}
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
    JsonSerializer[StockCreated],
    JsonSerializer[StockUpdated],
    JsonSerializer[StockArchived],
    JsonSerializer[Stock],
    // the replies use play-json as well
    JsonSerializer[Portfolio],
    JsonSerializer[PortfolioCreated],
    JsonSerializer[PortfolioUpdated],
    JsonSerializer[PortfolioArchived]
  )
}
