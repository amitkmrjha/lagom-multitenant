package com.example.helloworld.impl.entity

import akka.Done
import com.example.domain.Stock
import com.example.helloworld.impl.entity.StockStatus.StockStatus
import com.example.helloworld.impl.{HelloWorldCommand, HelloWorldEvent}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import play.api.Logger
import play.api.libs.json.{Format, Json}
import com.example.utils.JsonFormats.{enumFormat, singletonFormat}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType

final class StockEntity extends PersistentEntity {

  val logger = Logger(this.getClass)

  import com.example.helloworld.impl.entity.StockStatus._

  override type Command = StockCommand
  override type Event = StockEvent
  override type State = StockState

  override def initialState: StockState = StockState.notCreated

  override def behavior: Behavior = {
    case StockState(_, NotCreated) => notCreated
    case StockState(_, Active) => active
    case StockState(_, Archived) => deleted
    case _ => notCreated
  }

  private def deleteActions =
    Actions().onCommand[ArchiveStock, Done] {
      case (ArchiveStock(stock), ctx, _) =>
        ctx.thenPersist(StockArchived(stock))(_ => ctx.reply(Done))
    }.onEvent {
      case (StockArchived(stock), state) => StockState.archive(stock)
    }

  private def createActions =
    Actions().onCommand[CreateStock, Done] {
      case (CreateStock(stock), ctx, state) =>
        ctx.thenPersist(StockCreated(stock))(_ => ctx.reply(Done))
    }.onEvent {
      case (StockCreated(stock), state) => StockState.active(stock)
    }

  private def reviseActions =
    Actions().onCommand[UpdateStock, Done] {
      case (UpdateStock(stock), ctx, state) =>
        ctx.thenPersist(StockUpdated(stock))(_ => ctx.reply(Done))
    }.onEvent {
      case (StockUpdated(stock), state) => StockState.active(stock)
    }

  private def getStockActions = Actions().onReadOnlyCommand[GetStock.type, Option[Stock]] {
    case (GetStock, ctx, state) => ctx.reply(state.stock)
  }


  /**
    * Behavior for the not created state.
    */

  private val notCreated: Actions = {
    getStockActions orElse
      createActions orElse {
      Actions().onCommand[UpdateStock, Done] {
        case (_, ctx, _) =>
          ctx.invalidCommand(s"Cannot update a Stock that hasn't been active yet.")
          ctx.done
      }.onCommand[ArchiveStock, Done] {
        case (_, ctx, _) =>
          ctx.invalidCommand(s"Cannot delete a Stock that hasn't been active yet.")
          ctx.done
      }
    }
  }

  /**
    * Behavior for the active state.
    */

  private val active = {
    getStockActions orElse
      reviseActions orElse
      deleteActions orElse {
      Actions().onCommand[CreateStock, Done] {
        case (CreateStock(stock), ctx, state) =>
          ctx.invalidCommand(s"Cannot create a Stock, Stock with Key tenant id ${stock.tenantId} & portfolio id ${stock.stockId} is already active.")
          ctx.done
      }
    }
  }

  private val deleted = {
    getStockActions orElse {
      Actions()
        .onCommand[CreateStock, Done] {
          case (CreateStock(stock), ctx, state) =>
            ctx.invalidCommand(s"Cannot create a Stock that has been deleted.")
            ctx.done
        }.onCommand[UpdateStock, Done] {
        case (UpdateStock(stock), ctx, state) =>
          ctx.invalidCommand(s"Cannot update a Stock that has been deleted.")
          ctx.done
      }.onCommand[ArchiveStock, Done] {
        case (ArchiveStock(stock), ctx, state) =>
          ctx.invalidCommand(s"Cannot delete a Stock that has already been deleted.")
          ctx.done
      }
    }
  }
}

//Stock Status

object StockStatus extends Enumeration {
  type StockStatus = Value

  val NotCreated, Active, Archived = Value
  implicit val stockStatusFormat: Format[StockStatus] = enumFormat(StockStatus)
}

case class StockState(stock: Option[Stock], status: StockStatus)

object StockState {
  implicit val stockStateFormat: Format[StockState] = Json.format[StockState]

  val notCreated = StockState(None, StockStatus.NotCreated)

  def active(stock: Stock): StockState = StockState(Some(stock), StockStatus.Active)

  def archive(stock: Stock): StockState = StockState(None, StockStatus.Archived)
}


//Stock Command
sealed trait StockCommand extends HelloWorldCommand

case class CreateStock(stock: Stock) extends StockCommand with ReplyType[Done]

object CreateStock {
  implicit val createStockCommandFormat: Format[CreateStock] = Json.format[CreateStock]
}

case class UpdateStock(stock: Stock) extends StockCommand with ReplyType[Done]

object UpdateStock {
  implicit val updateStockCommandFormat: Format[UpdateStock] = Json.format[UpdateStock]
}

case object GetStock extends StockCommand with ReplyType[Option[Stock]] {
  implicit val getStockCommandFormat: Format[GetStock.type] = singletonFormat(GetStock)
}

case class ArchiveStock(stock: Stock) extends StockCommand with ReplyType[Done]

object ArchiveStock {
  implicit val deleteStockCommandFormat: Format[ArchiveStock] = Json.format[ArchiveStock]
}


//Stock Event
sealed trait StockEvent extends HelloWorldEvent

case class StockCreated(stock: Stock) extends StockEvent

object StockCreated {
  implicit val stockCreatedEventFormat: Format[StockCreated] = Json.format[StockCreated]
}

case class StockUpdated(stock: Stock) extends StockEvent

object StockUpdated {
  implicit val stockUpdatedEventFormat: Format[StockUpdated] = Json.format[StockUpdated]
}

case class StockArchived(stock: Stock) extends StockEvent

object StockArchived {
  implicit val stockDeletedEventFormat: Format[StockArchived] = Json.format[StockArchived]
}
