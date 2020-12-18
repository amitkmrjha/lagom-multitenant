package com.lightbend.lagom.internal.scaladsl.persistence.cassandra

import akka.{Done, NotUsed}
import akka.persistence.query.Offset
import akka.stream.ActorAttributes
import akka.stream.scaladsl.Flow
import com.datastax.driver.core.{BatchStatement, BoundStatement}
import com.lightbend.lagom.internal.persistence.cassandra.{CassandraOffsetDao, CassandraOffsetStore}
import com.lightbend.lagom.scaladsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, EventStreamElement}
import com.lightbend.lagom.scaladsl.persistence.cassandra.{CassandraSession, TenantBoundStatement, TenantCassandraSession, TenantDataBaseId}
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

  protected def invoke(handler: Handler, event: EventStreamElement[Event]): Future[(immutable.Seq[TenantBoundStatement],BoundStatement)]

  override def handle(): Flow[EventStreamElement[Event], Done, NotUsed] = {

    def executeStatements(statements: Seq[TenantBoundStatement], offSetBoundStatement:BoundStatement): Future[Done] = {
      val batchByTenant: Map[String, Seq[TenantBoundStatement]] = statements.groupBy(_.tenantId.tenantId)
      val tenantExecute : Seq[Future[Done]] = batchByTenant.map{
        case (k,v) =>
          if(!v.isEmpty){
            val batch = new BatchStatement
            // statements is never empty, there is at least the store offset statement
            // for simplicity we just use batch api (even if there is only one)
            batch.addAll(v.map(_.boundStatement).asJava)
            tenantSession.executeWriteBatch(batch)(TenantDataBaseId(k))
          }else{
            Future.successful(Done)
          }
      }.toSeq
      Future.sequence(tenantExecute).flatMap(_ => session.executeWrite(offSetBoundStatement))
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
        invoke(handler, elem).flatMap(p => executeStatements(p._1,p._2))
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
                               ): Future[(immutable.Seq[TenantBoundStatement],BoundStatement)] = {
    for {
      statements <- handler
        .asInstanceOf[EventStreamElement[Event] => Future[immutable.Seq[TenantBoundStatement]]]
        .apply(element)
    } yield {
      (statements ,offsetDao.bindSaveOffset(element.offset))
    }
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
  type Handler[Event] = (EventStreamElement[_ <: Event]) => Future[immutable.Seq[TenantBoundStatement]]

  def emptyHandler[Event]: Handler[Event] =
    (_) => Future.successful(immutable.Seq.empty[TenantBoundStatement])
}
