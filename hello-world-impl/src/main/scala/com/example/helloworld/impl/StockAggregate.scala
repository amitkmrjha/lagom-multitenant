package com.example.helloworld.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.example.domain.{Stock}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import play.api.libs.json.{Format, JsResult, JsValue, Json}

import java.time.LocalDateTime

object StockBehavior {

  /**
    * Given a sharding [[EntityContext]] this function produces an Akka [[Behavior]] for the aggregate.
    */
  def create(entityContext: EntityContext[StockCommand]): Behavior[StockCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId)
      .withTagger(
        // Using Akka Persistence Typed in Lagom requires tagging your events
        // in Lagom-compatible way so Lagom ReadSideProcessors and TopicProducers
        // can locate and follow the event streams.
        AkkaTaggerAdapter.fromLagom(entityContext, StockEvent.Tag)
      )

  }
  /*
   * This method is extracted to write unit tests that are completely independendant to Akka Cluster.
   */
  private[impl] def create(persistenceId: PersistenceId) = EventSourcedBehavior
    .withEnforcedReplies[StockCommand, StockEvent, StockState](
      persistenceId = persistenceId,
      emptyState = StockState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )
}

case class StockState(stockOption: Option[Stock], timestamp: String) {

  def isActive: Boolean     = !stockOption.isEmpty

  def applyCommand(cmd: StockCommand): ReplyEffect[StockEvent, StockState] = {
    if(isActive){
      cmd match {
        case GetStock(replyTo)  => onGet(replyTo)
        case x: UpdateStock => onUpdateStock(x)
        case x: ArchiveStock => onArchiveStock(x)
        case x: AddStock => reply(x.replyTo)(StockRejected(s"stock already exist with Id ${stockOption.get.stockId} and price ${stockOption.get.price}"))
      }
    }else{
      cmd match {
        case GetStock(replyTo)  => reply(replyTo)(StockSummary(None))
        case x: AddStock => onAddStock(x)
        case x: UpdateStock => reply(x.replyTo)(StockRejected(s"No Stock found with id ${x.stock.stockId}"))
        case x: ArchiveStock => reply(x.replyTo)(StockRejected(s"No Stock found with id ${x.stock.stockId}"))
      }
    }
  }
  private def onAddStock(cmd: AddStock): ReplyEffect[StockEvent, StockState] =
    Effect
      .persist(StockAdded(cmd.stock) )
      .thenReply(cmd.replyTo)(s => StockAccepted(StockSummary(s.stockOption)))

  private def onUpdateStock(cmd: UpdateStock): ReplyEffect[StockEvent, StockState] =
    Effect
      .persist(StockUpdated(cmd.stock) )
      .thenReply(cmd.replyTo)(s => StockAccepted(StockSummary(s.stockOption)))

  private def onArchiveStock(cmd: ArchiveStock): ReplyEffect[StockEvent, StockState] =
    Effect
      .persist(StockArchived(cmd.stock) )
      .thenReply(cmd.replyTo)(s => StockAccepted(StockSummary(s.stockOption)))

  private def onGet(replyTo: ActorRef[StockSummary]): ReplyEffect[StockEvent, StockState] = {
    reply(replyTo)(StockSummary(stockOption))
  }

  def applyEvent(evt: StockEvent): StockState =
    evt match {
      case x:StockAdded => onStockAdded(x)
      case x:StockUpdated => onStockUpdated(x)
      case x:StockArchived => onStockArchived(x)
    }

  private def onStockAdded(evt: StockAdded) =
    copy(Option(evt.stock), LocalDateTime.now().toString)
  private def onStockUpdated(evt: StockUpdated) =
    copy(Option(evt.stock), LocalDateTime.now().toString)
  private def onStockArchived(evt: StockArchived) =
    copy(None, LocalDateTime.now().toString)
}

object StockState {

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  def initial: StockState = StockState(None, LocalDateTime.now.toString)

  /**
    * The [[EventSourcedBehavior]] instances (aka Aggregates) run on sharded actors inside the Akka Cluster.
    * When sharding actors and distributing them across the cluster, each aggregate is
    * namespaced under a typekey that specifies a name and also the type of the commands
    * that sharded actor can receive.
    */
  val typeKey = EntityTypeKey[StockCommand]("StockAggregate")

  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the aggregate gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[StockState] = Json.format
}

/**
  * This interface defines all the events that the StockAggregate supports.
  */
sealed trait StockEvent extends AggregateEvent[StockEvent] {
  def aggregateTag: AggregateEventTag[StockEvent] = StockEvent.Tag
}

object StockEvent {
  val Tag: AggregateEventTag[StockEvent] = AggregateEventTag[StockEvent]
}

case class StockAdded(stock: Stock) extends StockEvent

object StockAdded {
  implicit val format: Format[StockAdded] = Json.format
}

case class StockUpdated(stock: Stock) extends StockEvent
object StockUpdated {
  implicit val format: Format[StockUpdated] = Json.format
}
case class StockArchived(stock: Stock) extends StockEvent
object StockArchived {
  implicit val format: Format[StockArchived] = Json.format
}

/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
trait StockCommandSerializable

/**
  * This interface defines all the commands that the HelloWorldAggregate supports.
  */
sealed trait StockCommand
  extends StockCommandSerializable

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[StockConfirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class AddStock(stock: Stock, replyTo: ActorRef[StockConfirmation])
  extends StockCommand

/**
  * A command to say hello to someone using the current greeting message.
  *
  * The reply type is String, and will contain the message to say to that
  * person.c
  */
case class UpdateStock(stock: Stock, replyTo: ActorRef[StockConfirmation])
  extends StockCommand

case class ArchiveStock(stock: Stock, replyTo: ActorRef[StockConfirmation])
  extends StockCommand

final case class GetStock(replyTo: ActorRef[StockSummary]) extends StockCommand


// Stock REPLIES
final case class StockSummary(stockOption:Option[Stock])
object StockSummary {
  implicit val format: Format[StockSummary] = Json.format
}

sealed trait StockConfirmation

case object StockConfirmation {
  implicit val format: Format[StockConfirmation] = new Format[StockConfirmation] {
    override def reads(json: JsValue): JsResult[StockConfirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[StockRejected](json)
      else
        Json.fromJson[StockAccepted](json)
    }

    override def writes(o: StockConfirmation): JsValue = {
      o match {
        case acc: StockAccepted => Json.toJson(acc)
        case rej: StockRejected => Json.toJson(rej)
      }
    }
  }
}

final case class StockAccepted(summary: StockSummary) extends StockConfirmation
 object StockAccepted {
  implicit val format: Format[StockAccepted] = Json.format
}

case class StockRejected(reason: String) extends StockConfirmation

object StockRejected {
  implicit val format: Format[StockRejected] = Json.format
}


