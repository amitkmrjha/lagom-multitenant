package com.example.helloworld.impl

import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventShards, AggregateEventTag, AggregateEventTagger}

trait HelloWorldEvent extends AggregateEvent[HelloWorldEvent] {
  override def aggregateTag: AggregateEventTagger[HelloWorldEvent] = HelloWorldEvent.Tag
}

object HelloWorldEvent {
  val NumShards = 1
  val Tag: AggregateEventShards[HelloWorldEvent] = AggregateEventTag.sharded[HelloWorldEvent](NumShards)
}