package com.example.helloworld.impl.entity

import akka.Done
import com.example.domain.Portfolio
import com.example.helloworld.impl.entity.PortfolioStatus.PortfolioStatus
import com.example.helloworld.impl.{HelloWorldCommand, HelloWorldEvent}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity
import play.api.Logger
import play.api.libs.json.{Format, Json}
import com.example.utils.JsonFormats.{enumFormat, singletonFormat}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType


final class PortfolioEntity extends  PersistentEntity{

  import com.example.helloworld.impl.entity.PortfolioStatus._

  val logger = Logger(this.getClass)

  override type Command = PortfolioCommand
  override type Event = PortfolioEvent
  override type State = PortfolioState

  override def initialState: PortfolioState = PortfolioState.notCreated

  override def behavior: Behavior = {
    case PortfolioState(_, NotCreated) => notCreated
    case PortfolioState(_, Active) => active
    case PortfolioState(_, Archived) => archived
    case _ => notCreated
  }

  private def archiveActions =
    Actions().onCommand[ArchivePortfolio, Done] {
      case (ArchivePortfolio(portfolio), ctx, _) =>
        ctx.thenPersist(PortfolioArchived(portfolio))(_ => ctx.reply(Done))
    }.onEvent {
      case (PortfolioArchived(portfolio), state) => PortfolioState.archived(portfolio)
    }

  private def createActions =
    Actions().onCommand[CreatePortfolio, Done] {
      case (CreatePortfolio(portfolio), ctx, state) =>
        ctx.thenPersist(PortfolioCreated(portfolio))(_ => ctx.reply(Done))
    }.onEvent {
      case (PortfolioCreated(portfolio), state) => PortfolioState.active(portfolio)
    }

  private def updateActions =
    Actions().onCommand[UpdatePortfolio, Done] {
      case (UpdatePortfolio(portfolio), ctx, state) =>
        ctx.thenPersist(PortfolioUpdated(portfolio))(_ => ctx.reply(Done))
    }.onEvent {
      case (PortfolioUpdated(portfolio), state) => PortfolioState.active(portfolio)
    }

  private def getPortfolioActions = Actions().onReadOnlyCommand[GetPortfolio.type , Option[Portfolio]] {
    case (GetPortfolio, ctx, state) => ctx.reply(state.portfolio)
  }


  /**
    * Behavior for the not created state.
    */

  private val notCreated: Actions = {
    getPortfolioActions orElse
      createActions orElse {
      Actions().onCommand[UpdatePortfolio, Done] {
        case (_, ctx, _) =>
          ctx.invalidCommand(s"Cannot update a Portfolio that hasn't been active yet.")
          ctx.done
      }.onCommand[ArchivePortfolio, Done] {
        case (_, ctx, _) =>
          ctx.invalidCommand(s"Cannot delete a Portfolio that hasn't been active yet.")
          ctx.done
      }
    }
  }

  /**
    * Behavior for the active state.
    */

  private val active = {
    getPortfolioActions orElse
      updateActions orElse
      archiveActions orElse {
      Actions().onCommand[CreatePortfolio, Done] {
        case (CreatePortfolio(portfolio), ctx, state) =>
          ctx.invalidCommand(s"Cannot create a Portfolio, Portfolio with tenant id ${portfolio.tenantId} & portfolio id ${portfolio.portfolioId} is already active.")
          ctx.done
      }
    }
  }

  private val archived = {
    getPortfolioActions orElse {
      Actions()
        .onCommand[CreatePortfolio, Done] {
          case (CreatePortfolio(portfolio), ctx, state) =>
            ctx.invalidCommand(s"Cannot create a Portfolio that has been archive.")
            ctx.done
        }.onCommand[UpdatePortfolio, Done] {
        case (UpdatePortfolio(portfolio), ctx, state) =>
          ctx.invalidCommand(s"Cannot update a Portfolio that has been archive.")
          ctx.done
      }.onCommand[ArchivePortfolio, Done] {
        case (ArchivePortfolio(portfolio), ctx, state) =>
          ctx.invalidCommand(s"Cannot delete a Portfolio that has already been archive.")
          ctx.done
      }
    }
  }
}


//Portfolio Status

object PortfolioStatus extends Enumeration {
  type PortfolioStatus = Value

  val NotCreated, Active, Archived = Value
  implicit val portfolioStatusFormat: Format[PortfolioStatus] = enumFormat(PortfolioStatus)
}

case class PortfolioState(portfolio: Option[Portfolio], status: PortfolioStatus)

object PortfolioState {
  implicit val portfolioStateFormat: Format[PortfolioState] = Json.format[PortfolioState]

  val notCreated = PortfolioState(None, PortfolioStatus.NotCreated)

  def active(portfolio: Portfolio): PortfolioState = PortfolioState(Some(portfolio), PortfolioStatus.Active)

  def archived(portfolio: Portfolio): PortfolioState = PortfolioState(None, PortfolioStatus.Archived)
}


//Portfolio Command
sealed trait PortfolioCommand extends HelloWorldCommand

case class CreatePortfolio(portfolio: Portfolio) extends PortfolioCommand with ReplyType[Done]

object CreatePortfolio {
  implicit val createPortfolioCommandFormat: Format[CreatePortfolio] = Json.format
}

case class UpdatePortfolio(portfolio: Portfolio) extends PortfolioCommand with ReplyType[Done]

object UpdatePortfolio {
  implicit val updatePortfolioCommandFormat: Format[UpdatePortfolio] = Json.format
}

case object GetPortfolio extends PortfolioCommand with ReplyType[Option[Portfolio]] {
  implicit val getPortfolioCommandFormat: Format[GetPortfolio.type] = singletonFormat(GetPortfolio)
}

case class ArchivePortfolio(portfolio: Portfolio) extends PortfolioCommand with ReplyType[Done]

object ArchivePortfolio {
  implicit val deletePortfolioCommandFormat: Format[ArchivePortfolio] = Json.format
}



//Portfolio Event
sealed trait PortfolioEvent extends HelloWorldEvent

case class PortfolioCreated(portfolio: Portfolio) extends PortfolioEvent

object PortfolioCreated {
  implicit val portfolioCreatedEventFormat: Format[PortfolioCreated] = Json.format
}

case class PortfolioUpdated(portfolio: Portfolio) extends PortfolioEvent

object PortfolioUpdated {
  implicit val portfolioUpdatedEventFormat: Format[PortfolioUpdated] = Json.format
}

case class PortfolioArchived(portfolio: Portfolio) extends PortfolioEvent

object PortfolioArchived {
  implicit val portfolioDeletedEventFormat: Format[PortfolioArchived] = Json.format
}