package csw.messages.ccs.commands

import java.util.concurrent.CompletableFuture

import akka.actor.Scheduler
import akka.typed.ActorRef
import akka.util.Timeout
import csw.messages.ComponentMessage
import csw.messages.params.models.RunId

import scala.compat.java8.FutureConverters.FutureOps
import scala.concurrent.ExecutionContext

class JComponentRef(val ref: ActorRef[ComponentMessage]) {
  private val componentRef = new ComponentRef(ref)

  def submit(controlCommand: ControlCommand, timeout: Timeout, scheduler: Scheduler): CompletableFuture[CommandResponse] =
    componentRef.submit(controlCommand)(timeout, scheduler).toJava.toCompletableFuture

  def oneway(controlCommand: ControlCommand, timeout: Timeout, scheduler: Scheduler): CompletableFuture[CommandResponse] =
    componentRef.submit(controlCommand)(timeout, scheduler).toJava.toCompletableFuture

  def getCommandResponse(commandRunId: RunId, timeout: Timeout, scheduler: Scheduler): CompletableFuture[CommandResponse] =
    componentRef.getCommandResponse(commandRunId)(timeout, scheduler).toJava.toCompletableFuture

  def submitAndGetCommandResponse(
      controlCommand: ControlCommand,
      timeout: Timeout,
      scheduler: Scheduler,
      ec: ExecutionContext
  ): CompletableFuture[CommandResponse] =
    componentRef.submitAndGetCommandResponse(controlCommand)(timeout, scheduler, ec).toJava.toCompletableFuture

  /*def submitManyAndGetCommandResponse(
      controlCommands: java.util.Set[ControlCommand],
      timeout: Timeout,
      scheduler: Scheduler,
      ec: ExecutionContext,
      mat: Materializer
  ): CompletableFuture[CommandResponse] =
    componentRef
      .submitManyAndGetCommandResponse(controlCommands.asScala.toSet)(timeout, scheduler, ec, mat)
      .toJava
      .toCompletableFuture*/
}
