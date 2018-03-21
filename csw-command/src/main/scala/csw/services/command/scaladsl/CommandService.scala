package csw.services.command.scaladsl

import akka.NotUsed
import akka.actor.Scheduler
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import csw.common.commands.CommandResponse.{Accepted, Completed, Error}
import csw.common.commands.matchers.MatcherResponses.{MatchCompleted, MatchFailed}
import csw.common.commands.matchers.{Matcher, StateMatcher}
import csw.common.commands.{CommandResponse, ControlCommand}
import csw.common.location.AkkaLocation
import csw.common.params.models.Id
import csw.common.params.states.CurrentState
import csw.common.scaladsl.CommandMessage.{Oneway, Submit}
import csw.common.scaladsl.{CommandResponseManagerMessage, ComponentMessage}

import scala.concurrent.{ExecutionContext, Future}

/**
 * A Command Service API of a csw component. This model provides method based APIs for command interactions with a component.
 *
 * @param componentLocation [[csw.common.location.AkkaLocation]] of the component
 */
class CommandService(componentLocation: AkkaLocation)(implicit val actorSystem: ActorSystem[_]) {

  private implicit val ec: ExecutionContext = actorSystem.executionContext
  private implicit val mat: Materializer    = ActorMaterializer()(actorSystem.toUntyped)
  private implicit val scheduler: Scheduler = actorSystem.scheduler

  private val component: ActorRef[ComponentMessage] = componentLocation.componentRef

  private val parallelism = 10

  /**
   * Submit a command and get a [[csw.common.commands.CommandResponse]] as a Future. The CommandResponse can be a response
   * of validation (Accepted, Invalid) or a final Response. In case of response as `Accepted`, final CommandResponse
   * can be obtained by using `subscribe` API.
   *
   * @param controlCommand the [[csw.common.commands.ControlCommand]] payload
   * @return a CommandResponse as a Future value
   */
  def submit(controlCommand: ControlCommand)(implicit timeout: Timeout): Future[CommandResponse] =
    component ? (Submit(controlCommand, _))

  /**
   * Submit multiple commands and get a Source of [[csw.common.commands.CommandResponse]] for all commands. The CommandResponse can be a response
   * of validation (Accepted, Invalid) or a final Response. In case of response as `Accepted`, final CommandResponse can be obtained by using `subscribe` API.
   *
   * @param controlCommands the set of [[csw.common.commands.ControlCommand]] payloads
   * @return a Source of CommandResponse as a stream of CommandResponses for all commands
   */
  def submitAll(controlCommands: Set[ControlCommand])(implicit timeout: Timeout): Source[CommandResponse, NotUsed] =
    Source(controlCommands).mapAsyncUnordered(parallelism)(submit)

  /**
   * Submit multiple commands and get one CommandResponse as a Future of [[csw.common.commands.CommandResponse]] for all commands. If all the commands were successful,
   * a CommandResponse as [[csw.common.commands.CommandResponse.Completed]] will be returned. If any one of the command fails, an [[csw.common.commands.CommandResponse.Error]]
   * will be returned.
   *
   * @param controlCommands the set of [[csw.common.commands.ControlCommand]] payloads
   * @return [[csw.common.commands.CommandResponse.Accepted]] or [[csw.common.commands.CommandResponse.Error]] CommandResponse as a Future
   */
  def submitAllAndGetResponse(controlCommands: Set[ControlCommand])(implicit timeout: Timeout): Future[CommandResponse] = {
    val value = Source(controlCommands).mapAsyncUnordered(parallelism)(submit)
    CommandResponse.aggregateResponse(value).map {
      case _: Completed  ⇒ CommandResponse.Accepted(Id())
      case otherResponse ⇒ otherResponse
    }
  }

  /**
   * Send a command as a Oneway and get a [[csw.common.commands.CommandResponse]] as a Future. The CommandResponse can be a response
   * of validation (Accepted, Invalid) or a final Response.
   *
   * @param controlCommand the [[csw.common.commands.ControlCommand]] payload
   * @return a CommandResponse as a Future value
   */
  def oneway(controlCommand: ControlCommand)(implicit timeout: Timeout): Future[CommandResponse] =
    component ? (Oneway(controlCommand, _))

  /**
   * Subscribe for the result of a long running command which was sent as Submit to get a [[csw.common.commands.CommandResponse]] as a Future
   *
   * @param commandRunId the runId of the command for which response is required
   * @return a CommandResponse as a Future value
   */
  def subscribe(commandRunId: Id)(implicit timeout: Timeout): Future[CommandResponse] =
    component ? (CommandResponseManagerMessage.Subscribe(commandRunId, _))

  /**
   * Query for the result of a long running command which was sent as Submit to get a [[csw.common.commands.CommandResponse]] as a Future
   *
   * @param commandRunId the runId of the command for which response is required
   * @return a CommandResponse as a Future value
   */
  def query(commandRunId: Id)(implicit timeout: Timeout): Future[CommandResponse] =
    component ? (CommandResponseManagerMessage.Query(commandRunId, _))

  /**
   * Submit a command and Subscribe for the result if it was successfully validated as `Accepted` to get a final [[csw.common.commands.CommandResponse]] as a Future
   *
   * @param controlCommand the [[csw.common.commands.ControlCommand]] payload
   * @return a CommandResponse as a Future value
   */
  def submitAndSubscribe(controlCommand: ControlCommand)(implicit timeout: Timeout): Future[CommandResponse] =
    submit(controlCommand).flatMap {
      case _: Accepted ⇒ subscribe(controlCommand.runId)
      case x           ⇒ Future.successful(x)
    }

  /**
   * Submit a command and match the published state from the component using a [[csw.common.commands.matchers.StateMatcher]]. If the match is successful a `Completed` response is
   * provided as a future. In case of a failure or unmatched state, `Error` CommandResponse is provided as a Future.
   *
   * @param controlCommand the [[csw.common.commands.ControlCommand]] payload
   * @param stateMatcher the StateMatcher implementation for matching received state against a demand state
   * @return a CommandResponse as a Future value
   */
  def onewayAndMatch(
      controlCommand: ControlCommand,
      stateMatcher: StateMatcher
  )(implicit timeout: Timeout): Future[CommandResponse] = {
    val matcher          = new Matcher(component, stateMatcher)
    val matcherResponseF = matcher.start
    oneway(controlCommand).flatMap {
      case _: Accepted ⇒
        matcherResponseF.map {
          case MatchCompleted  ⇒ Completed(controlCommand.runId)
          case MatchFailed(ex) ⇒ Error(controlCommand.runId, ex.getMessage)
        }
      case x ⇒
        matcher.stop()
        Future.successful(x)
    }
  }

  /**
   * Submit multiple commands and get final CommandResponse for all as a stream of CommandResponse. For long running commands, it will subscribe for the
   * result of those which were successfully validated as `Accepted` and get the final CommandResponse.
   *
   * @param controlCommands the [[csw.common.commands.ControlCommand]] payload
   * @return a Source of CommandResponse as a stream of CommandResponses for all commands
   */
  def submitAllAndSubscribe(controlCommands: Set[ControlCommand])(implicit timeout: Timeout): Source[CommandResponse, NotUsed] =
    Source(controlCommands).mapAsyncUnordered(parallelism)(submitAndSubscribe)

  /**
   * Submit multiple commands and get final CommandResponse for all as one CommandResponse. If all the commands were successful, a CommandResponse as
   * [[csw.common.commands.CommandResponse.Completed]] will be returned. If any one of the command fails, an [[csw.common.commands.CommandResponse.Error]]
   * will be returned. For long running commands, it will subscribe for the result of those which were successfully validated as `Accepted` and get the final CommandResponse.
   *
   * @param controlCommands the [[csw.common.commands.ControlCommand]] payload
   * @return a CommandResponse as a Future value
   */
  def submitAllAndGetFinalResponse(controlCommands: Set[ControlCommand])(implicit timeout: Timeout): Future[CommandResponse] = {
    val value = Source(controlCommands).mapAsyncUnordered(parallelism)(submitAndSubscribe)
    CommandResponse.aggregateResponse(value)
  }

  /**
   * Subscribe to the current state of a component corresponding to the [[csw.common.location.AkkaLocation]] of the component
   *
   * @param callback the action to be applied on the CurrentState element received as a result of subscription
   * @return a CurrentStateSubscription to stop the subscription
   */
  def subscribeCurrentState(callback: CurrentState ⇒ Unit): CurrentStateSubscription =
    new CurrentStateSubscription(component, callback)

}
