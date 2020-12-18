package com.lightbend.lagom.scaladsl.persistence.cassandra

import akka.Done
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, EventStreamElement}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraReadSide.ReadSideHandlerBuilder

import scala.collection.immutable
import scala.concurrent.Future
import scala.reflect.ClassTag

object TenantCassandraReadSide {

  trait TenantReadSideHandlerBuilder[Event <: AggregateEvent[Event]] {

    /**
      * Set a global prepare callback.
      *
      * @param callback The callback.
      * @return This builder for fluent invocation.
      * @see ReadSideHandler#globalPrepare()
      */
    def setGlobalPrepare(callback: () => Future[Done]): TenantReadSideHandlerBuilder[Event]

    /**
      * Set a prepare callback.
      *
      * @param callback The callback.
      * @return This builder for fluent invocation.
      * @see ReadSideHandler#prepare(AggregateEventTag)
      */
    def setPrepare(callback: AggregateEventTag[Event] => Future[Done]): TenantReadSideHandlerBuilder[Event]

    /**
      * Define the event handler that will be used for events of a given class.
      *
      * This variant allows for offsets to be consumed as well as their events.
      *
      * @tparam E The event type to handle.
      * @param handler    The function to handle the events.
      * @return This builder for fluent invocation
      */
    def setEventHandler[E <: Event: ClassTag](
                                               handler: EventStreamElement[E] => Future[immutable.Seq[TenantBoundStatement]]
                                             ): TenantReadSideHandlerBuilder[Event]

    /**
      * Build the read side handler.
      *
      * @return The read side handler.
      */
    def build(): ReadSideHandler[Event]
  }

}

trait TenantCassandraReadSide {

  /**
    * Create a builder for a Cassandra read side event handler.
    *
    * @param readSideId An identifier for this read side. This will be used to store offsets in the offset store.
    * @return The builder.
    */
  def builder[Event <: AggregateEvent[Event]](readSideId: String): TenantCassandraReadSide.TenantReadSideHandlerBuilder[Event]
}

