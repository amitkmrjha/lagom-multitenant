package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.Done
import akka.actor.ActorSystem
import com.datastax.driver.core.BoundStatement
import com.lightbend.lagom.internal.persistence.cassandra.CassandraOffsetStore
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, EventStreamElement}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.cassandra.TenantCassandraReadSide.TenantReadSideHandlerBuilder
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession, TenantBoundStatement, TenantCassandraReadSide, TenantCassandraSession}

import scala.collection.immutable
import scala.concurrent.Future
import scala.reflect.ClassTag

class TenantCassandraReadSideImpl(system: ActorSystem,
                                  session: CassandraSession,
                                  tenantSession: TenantCassandraSession,
                                  offsetStore: CassandraOffsetStore) extends TenantCassandraReadSide {

  private val dispatcher = system.settings.config.getString("lagom.persistence.read-side.use-dispatcher")
  implicit val ec        = system.dispatchers.lookup(dispatcher)

  override def builder[Event <: AggregateEvent[Event]](eventProcessorId: String): TenantReadSideHandlerBuilder[Event] = {
    new TenantReadSideHandlerBuilder[Event] {
      import TenantCassandraAutoReadSideHandler.Handler
      private var prepareCallback: AggregateEventTag[Event] => Future[Done] = tag => Future.successful(Done)
      private var globalPrepareCallback: () => Future[Done]                 = () => Future.successful(Done)
      private var handlers                                                  = Map.empty[Class[_ <: Event], Handler[Event]]

      override def setGlobalPrepare(callback: () => Future[Done]): TenantReadSideHandlerBuilder[Event] = {
        globalPrepareCallback = callback
        this
      }

      override def setPrepare(callback: (AggregateEventTag[Event]) => Future[Done]): TenantReadSideHandlerBuilder[Event] = {
        prepareCallback = callback
        this
      }

      override def setEventHandler[E <: Event: ClassTag](
                                                          handler: EventStreamElement[E] => Future[immutable.Seq[TenantBoundStatement]]
                                                        ): TenantReadSideHandlerBuilder[Event] = {
        val eventClass = implicitly[ClassTag[E]].runtimeClass.asInstanceOf[Class[Event]]
        handlers += (eventClass -> handler.asInstanceOf[Handler[Event]])
        this
      }

      override def build(): ReadSideHandler[Event] = {
        new TenantCassandraAutoReadSideHandler[Event](
          session,
          tenantSession,
          offsetStore,
          handlers,
          globalPrepareCallback,
          prepareCallback,
          eventProcessorId,
          dispatcher
        )
      }

    }
  }
}