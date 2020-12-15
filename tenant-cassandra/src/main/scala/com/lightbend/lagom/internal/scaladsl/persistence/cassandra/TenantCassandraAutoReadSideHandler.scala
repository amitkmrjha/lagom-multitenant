package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.{Done, NotUsed}
import akka.persistence.query.Offset
import akka.stream.ActorAttributes
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.{BatchStatement, BoundStatement}
import com.lightbend.lagom.internal.persistence.cassandra.{CassandraOffsetDao, CassandraOffsetStore}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, EventStreamElement}
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession, TenantCassandraSession}
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._

abstract class TenantCassandraReadSideHandler[Event <: AggregateEvent[Event], Handler](
                                                                                  session: CassandraSession,
                                                                                  tenantSession: TenantCassandraSession,
                                                                                  handlers: Map[Class[_ <: Event], Handler],
                                                                                  dispatcher: String
                                                                                )(implicit ec: ExecutionContext)
  extends ReadSideHandler[Event] {
  private val log = LoggerFactory.getLogger(this.getClass)

  protected def invoke(handler: Handler, event: EventStreamElement[Event]): Future[immutable.Seq[BoundStatement]]

  override def handle(): Flow[EventStreamElement[Event], Done, NotUsed] = {
    def executeStatements(statements: Seq[BoundStatement]): Future[Done] = {
      val batch = new BatchStatement
      // statements is never empty, there is at least the store offset statement
      // for simplicity we just use batch api (even if there is only one)
      batch.addAll(statements.asJava)
      tenantSession.executeWriteBatch(batch)
      //session.executeWriteBatch(batch)
    }

    Flow[EventStreamElement[Event]]
      .mapAsync(parallelism = 1) { elem =>
        val eventClass = elem.event.getClass

        val handler =
          handlers.getOrElse(
            // lookup handler
            eventClass,
            // fallback to empty handler if none
            {
              if (log.isDebugEnabled()) log.debug("Unhandled event [{}]", eventClass.getName)
              TenantCassandraAutoReadSideHandler.emptyHandler.asInstanceOf[Handler]
            }
          )

        invoke(handler, elem).flatMap(executeStatements)
      }
      .withAttributes(ActorAttributes.dispatcher(dispatcher))
  }
}

class TenantCassandraAutoReadSideHandler [Event <: AggregateEvent[Event]](
                                                                           session: CassandraSession,
                                                                           tenantSession: TenantCassandraSession,
                                                                           offsetStore: CassandraOffsetStore,
                                                                           handlers: Map[Class[_ <: Event], TenantCassandraAutoReadSideHandler.Handler[Event]],
                                                                           globalPrepareCallback: () => Future[Done],
                                                                           prepareCallback: AggregateEventTag[Event] => Future[Done],
                                                                           readProcessorId: String,
                                                                           dispatcher: String
                                                                         )(implicit ec: ExecutionContext)
  extends TenantCassandraReadSideHandler[Event, TenantCassandraAutoReadSideHandler.Handler[Event]](
    session,
    tenantSession,
    handlers,
    dispatcher
  ) {
  import TenantCassandraAutoReadSideHandler.Handler

  @volatile
  private var offsetDao: CassandraOffsetDao = _

  protected override def invoke(
                                 handler: Handler[Event],
                                 element: EventStreamElement[Event]
                               ): Future[immutable.Seq[BoundStatement]] = {
    for {
      statements <- handler
        .asInstanceOf[EventStreamElement[Event] => Future[immutable.Seq[BoundStatement]]]
        .apply(element)
    } yield statements :+ offsetDao.bindSaveOffset(element.offset)
  }

  protected def offsetStatement(offset: Offset): immutable.Seq[BoundStatement] =
    immutable.Seq(offsetDao.bindSaveOffset(offset))

  override def globalPrepare(): Future[Done] = {
    globalPrepareCallback.apply()
  }

  override def prepare(tag: AggregateEventTag[Event]): Future[Offset] = {
    for {
      _   <- prepareCallback.apply(tag)
      dao <- offsetStore.prepare(readProcessorId, tag.tag)
    } yield {
      offsetDao = dao
      dao.loadedOffset
    }
  }
}

object TenantCassandraAutoReadSideHandler {
  type Handler[Event] = (EventStreamElement[_ <: Event]) => Future[immutable.Seq[BoundStatement]]

  def emptyHandler[Event]: Handler[Event] =
    (_) => Future.successful(immutable.Seq.empty[BoundStatement])
}
