package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.persistence.AggregateEvent
import com.lightbend.lagom.scaladsl.persistence.AggregateEventShards
import com.lightbend.lagom.scaladsl.persistence.AggregateEventTag


trait HelloWorldEvent extends AggregateEvent[HelloWorldEvent] {
  override def aggregateTag: AggregateEventShards[HelloWorldEvent] = HelloWorldEvent.Tag

}

object HelloWorldEvent {
  val NumShards = 4
  val Tag = AggregateEventTag.sharded[HelloWorldEvent](NumShards)
}