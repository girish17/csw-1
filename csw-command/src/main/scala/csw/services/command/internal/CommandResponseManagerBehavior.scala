package csw.services.command.internal

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import csw.common.ccs.commands.{CommandCorrelation, CommandResponseManagerState}
import csw.common.commands.CommandResponse.CommandNotAvailable
import csw.common.commands.CommandResultType.{Final, Intermediate}
import csw.common.commands.{CommandResponse, CommandResultType}
import csw.common.params.models.Id
import csw.common.scaladsl.CommandResponseManagerMessage
import csw.common.scaladsl.CommandResponseManagerMessage._
import csw.services.logging.scaladsl.{Logger, LoggerFactory}

/**
 * The Behavior of a Command Response Manager, represented as a mutable behavior. This behavior will be created as an actor.
 * There will be one CommandResponseManger for a given component which will provide an interface to interact with the status
 * and result of a submitted command.
 *
 * This class defines the behavior of CommandResponseManagerActor and is responsible for adding/updating/querying command result.
 * When component receives command of type Submit, then framework (ComponentBehavior - TLA) will add a entry of this command
 * with its validation status into CommandResponseManager.
 *
 * In case of short running or immediate command,
 * validation response will be of type final result which can either be of type
 * [[csw.common.commands.CommandResultType.Positive]] or [[csw.common.commands.CommandResultType.Negative]]
 *
 * In case of long running command, validation response will be of type [[csw.common.commands.CommandResultType.Intermediate]]
 * then it is the responsibility of component writer to update its final command status later on
 * with [[csw.common.commands.CommandResponse]] which should be of type
 * [[csw.common.commands.CommandResultType.Positive]] or [[csw.common.commands.CommandResultType.Negative]]
 *
 * CommandResponseManager also provides subscribe API.
 * One of the use case for this is when Assembly splits top level command into two sub commands and forwards them to two different HCD's.
 * In this case, Assembly can register its interest in the final [[csw.common.commands.CommandResponse]]
 * from two HCD's when these sub commands completes, using subscribe API. And once Assembly receives final command response
 * from both the HCD's then it can update Top level command with final [[csw.common.commands.CommandResponse]]
 *
 * @param ctx             The Actor Context under which the actor instance of this behavior is created
 * @param loggerFactory   The factory for creating [[csw.services.logging.scaladsl.Logger]] instance
 */
private[command] class CommandResponseManagerBehavior(
    ctx: ActorContext[CommandResponseManagerMessage],
    loggerFactory: LoggerFactory
) extends Behaviors.MutableBehavior[CommandResponseManagerMessage] {
  private val log: Logger = loggerFactory.getLogger(ctx)

  private[command] var commandResponseManagerState: CommandResponseManagerState = CommandResponseManagerState(Map.empty)
  private[command] var commandCoRelation: CommandCorrelation                    = CommandCorrelation(Map.empty, Map.empty)

  override def onMessage(msg: CommandResponseManagerMessage): Behavior[CommandResponseManagerMessage] = {
    msg match {
      case AddOrUpdateCommand(commandId, cmdStatus)  ⇒ addOrUpdateCommand(commandId, cmdStatus)
      case AddSubCommand(parentRunId, childRunId)    ⇒ commandCoRelation = commandCoRelation.add(parentRunId, childRunId)
      case UpdateSubCommand(subCommandId, cmdStatus) ⇒ updateSubCommand(subCommandId, cmdStatus)
      case Query(runId, replyTo)                     ⇒ replyTo ! commandResponseManagerState.get(runId)
      case Subscribe(runId, replyTo)                 ⇒ subscribe(runId, replyTo)
      case Unsubscribe(runId, subscriber) ⇒
        commandResponseManagerState = commandResponseManagerState.unSubscribe(runId, subscriber)
      case SubscriberTerminated(subscriber) ⇒
        commandResponseManagerState = commandResponseManagerState.removeSubscriber(subscriber)
      case GetCommandCorrelation(replyTo)          ⇒ replyTo ! commandCoRelation
      case GetCommandResponseManagerState(replyTo) ⇒ replyTo ! commandResponseManagerState
    }
    this
  }

  private def addOrUpdateCommand(commandId: Id, commandResponse: CommandResponse): Unit =
    commandResponseManagerState.get(commandId) match {
      case _: CommandNotAvailable ⇒ commandResponseManagerState = commandResponseManagerState.add(commandId, commandResponse)
      case _                      ⇒ updateCommand(commandId, commandResponse)
    }

  private def updateCommand(commandId: Id, commandResponse: CommandResponse): Unit = {
    val currentResponse = commandResponseManagerState.get(commandId)
    if (currentResponse.resultType == CommandResultType.Intermediate && currentResponse != commandResponse) {
      commandResponseManagerState = commandResponseManagerState.updateCommandStatus(commandResponse)
      publishToSubscribers(commandResponse, commandResponseManagerState.cmdToCmdStatus(commandResponse.runId).subscribers)
    }
  }

  private def updateSubCommand(subCommandId: Id, commandResponse: CommandResponse): Unit = {
    // If the sub command has a parent command, fetch the current status of parent command from command status service
    commandCoRelation
      .getParent(commandResponse.runId)
      .foreach(parentId ⇒ updateParent(parentId, commandResponse))
  }

  private def updateParent(parentRunId: Id, childCommandResponse: CommandResponse): Unit =
    (commandResponseManagerState.get(parentRunId).resultType, childCommandResponse.resultType) match {
      // If the child command receives a negative result, the result of the parent command need not wait for the
      // result from other sub commands
      case (CommandResultType.Intermediate, CommandResultType.Negative) ⇒
        updateCommand(parentRunId, CommandResponse.withRunId(parentRunId, childCommandResponse))
      // If the child command receives a positive result, the result of the parent command needs to be evaluated based
      // on the result from other sub commands
      case (CommandResultType.Intermediate, CommandResultType.Positive) ⇒
        updateParentForChild(parentRunId, childCommandResponse)
      case _ ⇒ log.debug("Parent Command is already updated with a Final response. Ignoring this update.")
    }

  private def updateParentForChild(parentRunId: Id, childCommandResponse: CommandResponse): Unit =
    childCommandResponse.resultType match {
      case _: Final ⇒
        commandCoRelation = commandCoRelation.remove(parentRunId, childCommandResponse.runId)
        if (!commandCoRelation.hasChildren(parentRunId))
          updateCommand(parentRunId, CommandResponse.withRunId(parentRunId, childCommandResponse))
      case _ ⇒ log.debug("Validation response will not affect status of Parent command.")
    }

  private def publishToSubscribers(commandResponse: CommandResponse, subscribers: Set[ActorRef[CommandResponse]]): Unit = {
    commandResponse.resultType match {
      case _: Final ⇒
        subscribers.foreach(_ ! commandResponse)
      case Intermediate ⇒
        // Do not send updates for validation response as it is send by the framework
        log.debug("Validation response will not affect status of Parent command.")
    }
  }

  private def subscribe(runId: Id, replyTo: ActorRef[CommandResponse]): Unit = {
    ctx.watchWith(replyTo, SubscriberTerminated(replyTo))
    commandResponseManagerState = commandResponseManagerState.subscribe(runId, replyTo)
    publishToSubscribers(commandResponseManagerState.get(runId), Set(replyTo))
  }
}
