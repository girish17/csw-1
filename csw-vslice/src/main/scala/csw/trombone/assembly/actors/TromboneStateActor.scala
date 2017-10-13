package csw.trombone.assembly.actors

import akka.typed.scaladsl.Actor.MutableBehavior
import akka.typed.scaladsl.{Actor, ActorContext}
import akka.typed.{ActorRef, Behavior}
import csw.messages.params.generics.KeyType.ChoiceKey
import csw.messages.params.generics.{KeyType, _}
import csw.messages.params.models.Choice
import csw.trombone.assembly.actors.TromboneStateActor.TromboneStateMsg

object TromboneStateActor {

  def make(): Behavior[TromboneStateMsg] = Actor.mutable(ctx ⇒ new TromboneStateActor(ctx))

  // Keys for state telemetry item
  val cmdUninitialized               = Choice("uninitialized")
  val cmdReady                       = Choice("ready")
  val cmdBusy                        = Choice("busy")
  val cmdContinuous                  = Choice("continuous")
  val cmdError                       = Choice("error")
  val cmdKey                         = ChoiceKey.make("cmd", cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError)
  val cmdDefault                     = cmdItem(cmdUninitialized)
  def cmd(ts: TromboneState): Choice = ts.cmd.head

  def cmdItem(ch: Choice): Parameter[Choice] = cmdKey.set(ch)

  val moveUnindexed                   = Choice("unindexed")
  val moveIndexing                    = Choice("indexing")
  val moveIndexed                     = Choice("indexed")
  val moveMoving                      = Choice("moving")
  val moveKey                         = ChoiceKey.make("move", moveUnindexed, moveIndexing, moveIndexed, moveMoving)
  val moveDefault                     = moveItem(moveUnindexed)
  def move(ts: TromboneState): Choice = ts.move.head

  def moveItem(ch: Choice): Parameter[Choice] = moveKey.set(ch)

  def sodiumKey                               = KeyType.BooleanKey.make("sodiumLayer")
  val sodiumLayerDefault                      = sodiumItem(false)
  def sodiumLayer(ts: TromboneState): Boolean = ts.sodiumLayer.head

  def sodiumItem(flag: Boolean): Parameter[Boolean] = sodiumKey.set(flag)

  def nssKey                          = KeyType.BooleanKey.make("nss")
  val nssDefault                      = nssItem(false)
  def nss(ts: TromboneState): Boolean = ts.nss.head

  def nssItem(flag: Boolean): Parameter[Boolean] = nssKey.set(flag)

  val defaultTromboneState = TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault)

  case class TromboneState(
      cmd: Parameter[Choice],
      move: Parameter[Choice],
      sodiumLayer: Parameter[Boolean],
      nss: Parameter[Boolean]
  )

  sealed trait TromboneStateMsg

  case class SetState(tromboneState: TromboneState, replyTo: ActorRef[StateWasSet]) extends TromboneStateMsg

  object SetState {

    def apply(
        cmd: Parameter[Choice],
        move: Parameter[Choice],
        sodiumLayer: Parameter[Boolean],
        nss: Parameter[Boolean],
        replyTo: ActorRef[StateWasSet]
    ): SetState = SetState(TromboneState(cmd, move, sodiumLayer, nss), replyTo)

    def apply(cmd: Choice, move: Choice, sodiumLayer: Boolean, nss: Boolean, replyTo: ActorRef[StateWasSet]): SetState =
      SetState(TromboneState(cmdItem(cmd), moveItem(move), sodiumItem(sodiumLayer), nssItem(nss)), replyTo)
  }

  case class GetState(replyTo: ActorRef[TromboneState]) extends TromboneStateMsg

  case class StateWasSet(wasSet: Boolean)
}

class TromboneStateActor(ctx: ActorContext[TromboneStateMsg]) extends MutableBehavior[TromboneStateMsg] {

  import TromboneStateActor._

  var currentState: TromboneState = TromboneState(cmdDefault, moveDefault, sodiumLayerDefault, nssDefault)

  override def onMessage(msg: TromboneStateMsg): Behavior[TromboneStateMsg] = msg match {
    case SetState(tromboneState, replyTo) =>
      if (tromboneState != currentState) {
        ctx.system.eventStream.publish(tromboneState)
        currentState = tromboneState
        replyTo ! StateWasSet(true)
        this
      } else {
        replyTo ! StateWasSet(false)
        this
      }
    case GetState(replyTo) =>
      replyTo ! currentState
      this
  }
}