package com.example.helloworld.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.{EntityContext, EntityTypeKey}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.example.domain.{Holding, Portfolio, Stock}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import play.api.libs.json.{Format, JsResult, JsValue, Json}

import java.time.LocalDateTime


/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
trait PortfolioCommandSerializable

/**
  * This interface defines all the commands that the HelloWorldAggregate supports.
  */
sealed trait PortfolioCommand
  extends PortfolioCommandSerializable

/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[StockConfirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */
case class AddPortfolio(portfolio: Portfolio, replyTo: ActorRef[PortfolioConfirmation])
  extends PortfolioCommand

/**
  * A command to say hello to someone using the current greeting message.
  *
  * The reply type is String, and will contain the message to say to that
  * person.c
  */
case class UpdatePortfolio(portfolio: Portfolio, replyTo: ActorRef[PortfolioConfirmation])
  extends PortfolioCommand

case class AddStockToPortfolio(tenantId:String,holding:Holding, replyTo: ActorRef[PortfolioConfirmation])
  extends PortfolioCommand

case class RemoveStockFromPortfolio(tenantId:String,holding:Holding, replyTo: ActorRef[PortfolioConfirmation])
  extends PortfolioCommand

case class ArchivePortfolio(portfolio: Portfolio, replyTo: ActorRef[PortfolioConfirmation])
  extends PortfolioCommand

final case class GetPortfolio(replyTo: ActorRef[PortfolioSummary]) extends PortfolioCommand

//Event
/**
  * This interface defines all the events that the StockAggregate supports.
  */
sealed trait PortfolioEvent extends AggregateEvent[PortfolioEvent] {
  def aggregateTag: AggregateEventTag[PortfolioEvent] = PortfolioEvent.Tag
}

object PortfolioEvent {
  val Tag: AggregateEventTag[PortfolioEvent] = AggregateEventTag[PortfolioEvent]
}

case class PortfolioAdded(portfolio: Portfolio) extends PortfolioEvent

object PortfolioAdded {
  implicit val format: Format[PortfolioAdded] = Json.format
}

case class PortfolioUpdated(portfolio: Portfolio) extends PortfolioEvent
object PortfolioUpdated {
  implicit val format: Format[PortfolioUpdated] = Json.format
}
case class StockAddedToPortfolio(tenantId:String,holding:Holding) extends PortfolioEvent
object StockAddedToPortfolio {
  implicit val format: Format[StockAddedToPortfolio] = Json.format
}

case class StockRemovedFromPortfolio(tenantId:String,holding:Holding) extends PortfolioEvent
object StockRemovedFromPortfolio {
  implicit val format: Format[StockRemovedFromPortfolio] = Json.format
}

case class PortfolioArchived(portfolio: Portfolio) extends PortfolioEvent
object PortfolioArchived {
  implicit val format: Format[PortfolioArchived] = Json.format
}


// Stock REPLIES
final case class PortfolioSummary(portfolioOption:Option[Portfolio])
object PortfolioSummary {
  implicit val format: Format[PortfolioSummary] = Json.format
}

sealed trait PortfolioConfirmation

case object PortfolioConfirmation {
  implicit val format: Format[PortfolioConfirmation] = new Format[PortfolioConfirmation] {
    override def reads(json: JsValue): JsResult[PortfolioConfirmation] = {
      if ((json \ "reason").isDefined)
        Json.fromJson[PortfolioRejected](json)
      else
        Json.fromJson[PortfolioAccepted](json)
    }

    override def writes(o: PortfolioConfirmation): JsValue = {
      o match {
        case acc: PortfolioAccepted => Json.toJson(acc)
        case rej: PortfolioRejected => Json.toJson(rej)
      }
    }
  }
}

final case class PortfolioAccepted(summary: PortfolioSummary) extends PortfolioConfirmation
object PortfolioAccepted {
  implicit val format: Format[PortfolioAccepted] = Json.format
}

case class PortfolioRejected(reason: String) extends PortfolioConfirmation

object PortfolioRejected {
  implicit val format: Format[PortfolioRejected] = Json.format
}

case class PortfolioState(portfolioOption: Option[Portfolio], timestamp: String) {

  def isActive: Boolean     = !portfolioOption.isEmpty

  def applyCommand(cmd: PortfolioCommand): ReplyEffect[PortfolioEvent, PortfolioState] = {
    if(isActive){
      cmd match {
        case GetPortfolio(replyTo)  => onGet(replyTo)
        case x: AddPortfolio => reply(x.replyTo)(PortfolioRejected(s"Portfolio already exist with Id ${portfolioOption.get.tenantId}."))
        case x: UpdatePortfolio => onUpdatePortfolio(x)
        case x: AddStockToPortfolio => onAddStockToPortfolio(x)
        case x: RemoveStockFromPortfolio => onRemoveStockFromPortfolio(x)
        case x: ArchivePortfolio => onArchivePortfolio(x)
      }
    }else{
      cmd match {
        case GetPortfolio(replyTo)  => reply(replyTo)(PortfolioSummary(None))
        case x: AddPortfolio => onAddPortfolio(x)
        case x: UpdatePortfolio => reply(x.replyTo)(PortfolioRejected(s"No portfolio found with id ${x.portfolio.tenantId}"))
        case x: AddStockToPortfolio => reply(x.replyTo)(PortfolioRejected(s"No portfolio found with id ${x.tenantId}"))
        case x: RemoveStockFromPortfolio => reply(x.replyTo)(PortfolioRejected(s"No portfolio found with id ${x.tenantId}"))
        case x: ArchivePortfolio => reply(x.replyTo)(PortfolioRejected(s"No portfolio found with id ${x.portfolio.tenantId}"))
      }
    }
  }
  private def onAddPortfolio(cmd: AddPortfolio): ReplyEffect[PortfolioEvent, PortfolioState] =
    Effect
      .persist(PortfolioAdded(cmd.portfolio) )
      .thenReply(cmd.replyTo)(s => PortfolioAccepted(PortfolioSummary(s.portfolioOption)))

  private def onUpdatePortfolio(cmd: UpdatePortfolio): ReplyEffect[PortfolioEvent, PortfolioState] =
    Effect
      .persist(PortfolioUpdated(cmd.portfolio) )
      .thenReply(cmd.replyTo)(s => PortfolioAccepted(PortfolioSummary(s.portfolioOption)))

  private def onAddStockToPortfolio(cmd: AddStockToPortfolio): ReplyEffect[PortfolioEvent, PortfolioState] =
    Effect
      .persist(StockAddedToPortfolio(cmd.tenantId,cmd.holding) )
      .thenReply(cmd.replyTo)(s => PortfolioAccepted(PortfolioSummary(s.portfolioOption)))

  private def onRemoveStockFromPortfolio(cmd: RemoveStockFromPortfolio): ReplyEffect[PortfolioEvent, PortfolioState] =
    Effect
      .persist(StockRemovedFromPortfolio(cmd.tenantId,cmd.holding) )
      .thenReply(cmd.replyTo)(s => PortfolioAccepted(PortfolioSummary(s.portfolioOption)))

  private def onArchivePortfolio(cmd: ArchivePortfolio): ReplyEffect[PortfolioEvent, PortfolioState] =
    Effect
      .persist(PortfolioArchived(cmd.portfolio) )
      .thenReply(cmd.replyTo)(s => PortfolioAccepted(PortfolioSummary(s.portfolioOption)))

  private def onGet(replyTo: ActorRef[PortfolioSummary]): ReplyEffect[PortfolioEvent, PortfolioState] = {
    reply(replyTo)(PortfolioSummary(portfolioOption))
  }

  def applyEvent(evt: PortfolioEvent): PortfolioState =
    evt match {
      case x:PortfolioAdded => onPortfolioAdded(x)
      case x:PortfolioUpdated=> onPortfolioUpdated(x)
      case x:StockAddedToPortfolio => onStockAddedToPortfolio(x)
      case x:StockRemovedFromPortfolio => onStockRemovedFromPortfolio(x)
      case x:PortfolioArchived => onPortfolioArchived(x)
    }

  private def onPortfolioAdded(evt: PortfolioAdded) =
    copy(Option(evt.portfolio), LocalDateTime.now().toString)
  private def onPortfolioUpdated(evt: PortfolioUpdated) =
    copy(Option(evt.portfolio), LocalDateTime.now().toString)
  private def onStockAddedToPortfolio(evt: StockAddedToPortfolio) =
    copy(addToPortfolio(evt.tenantId,evt.holding), LocalDateTime.now().toString)
  private def onStockRemovedFromPortfolio(evt: StockRemovedFromPortfolio) =
    copy(removeFromPortfolio(evt.tenantId,evt.holding), LocalDateTime.now().toString)
  private def onPortfolioArchived(evt: PortfolioArchived) = {
    copy(None, LocalDateTime.now().toString)
  }

  private def addToPortfolio(tenantId:String,holding:Holding):Option[Portfolio] =
    portfolioOption
      .map{p =>
        val currentHoldings = p.holdings
        val newHoldings = currentHoldings.filter(e => e.stockId!=holding.stockId):+ holding
        p.copy(holdings = newHoldings)
      }

  private def removeFromPortfolio(tenantId:String,holding:Holding):Option[Portfolio] =
    portfolioOption
      .map(p =>  p.copy(holdings = p.holdings.filter(e => e!=holding) ))
}

object PortfolioState {

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  def initial: PortfolioState = PortfolioState(None, LocalDateTime.now.toString)

  /**
    * The [[EventSourcedBehavior]] instances (aka Aggregates) run on sharded actors inside the Akka Cluster.
    * When sharding actors and distributing them across the cluster, each aggregate is
    * namespaced under a typekey that specifies a name and also the type of the commands
    * that sharded actor can receive.
    */
  val typeKey = EntityTypeKey[PortfolioCommand]("PortfolioAggregate")

  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the aggregate gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[PortfolioState] = Json.format
}

object PortfolioBehavior {

  /**
    * Given a sharding [[EntityContext]] this function produces an Akka [[Behavior]] for the aggregate.
    */
  def create(entityContext: EntityContext[PortfolioCommand]): Behavior[PortfolioCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId)
      .withTagger(
        // Using Akka Persistence Typed in Lagom requires tagging your events
        // in Lagom-compatible way so Lagom ReadSideProcessors and TopicProducers
        // can locate and follow the event streams.
        AkkaTaggerAdapter.fromLagom(entityContext, PortfolioEvent.Tag)
      )

  }
  /*
   * This method is extracted to write unit tests that are completely independendant to Akka Cluster.
   */
  private[impl] def create(persistenceId: PersistenceId) = EventSourcedBehavior
    .withEnforcedReplies[PortfolioCommand, PortfolioEvent, PortfolioState](
      persistenceId = persistenceId,
      emptyState = PortfolioState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )
}