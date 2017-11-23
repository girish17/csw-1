package csw.command

import java.util.UUID

import akka.actor.Scheduler
import akka.typed.ActorSystem
import akka.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.typed.testkit.TestKitSettings
import akka.typed.testkit.scaladsl.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.framework.internal.wiring.{Container, FrameworkWiring, Standalone}
import csw.messages.CommandMessage.Submit
import csw.messages.CommandResponseManagerMessage
import csw.messages.SupervisorLockMessage.Lock
import csw.messages.ccs.CommandIssue.ComponentLockedIssue
import csw.messages.ccs.commands.CommandResponse.{Accepted, Completed, Invalid, NotAllowed}
import csw.messages.ccs.commands.{CommandResponse, Observe, Setup}
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location.{ComponentId, ComponentType}
import csw.messages.models.LockingResponse
import csw.messages.params.models.ObsId
import csw.services.ccs.common.ActorRefExts.RichActor
import csw.services.location.helpers.{LSNodeSpec, TwoMembersAndSeed}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

class CommandServiceTestMultiJvm1 extends CommandServiceTest(0)
class CommandServiceTestMultiJvm2 extends CommandServiceTest(0)
class CommandServiceTestMultiJvm3 extends CommandServiceTest(0)

// DEOPSCSW-222: Locking a component for a specific duration
// DEOPSCSW-313: Support short running actions by providing immediate response
class CommandServiceTest(ignore: Int) extends LSNodeSpec(config = new TwoMembersAndSeed) {

  import config._
  import csw.common.components.command.ComponentStateForCommand._

  implicit val actorSystem: ActorSystem[_]  = system.toTyped
  implicit val ec: ExecutionContextExecutor = actorSystem.executionContext
  implicit val timeout: Timeout             = 5.seconds
  implicit val scheduler: Scheduler         = actorSystem.scheduler
  implicit val testkit: TestKitSettings     = TestKitSettings(actorSystem)

  test("sender of command should receive appropriate responses") {

    runOn(seed) {
      // cluster seed is running on jvm-1
      enterBarrier("spawned")

      // resolve assembly running in jvm-3 and send setup command expecting immediate command completion response
      val assemblyLocF = locationService.resolve(AkkaConnection(ComponentId("Assembly", ComponentType.Assembly)), 5.seconds)
      val assemblyRef  = Await.result(assemblyLocF, 10.seconds).map(_.componentRef()).get

      enterBarrier("immediate-setup-complete")
      enterBarrier("assembly-locked")

      val obsId            = ObsId("Obs002")
      val cmdResponseProbe = TestProbe[CommandResponse]

      // try to send a command to assembly which is already locked
      val assemblyObserve = Observe(obsId, acceptedCmdPrefix)
      assemblyRef ! Submit(assemblyObserve, cmdResponseProbe.ref)
      val response = cmdResponseProbe.expectMsgType[NotAllowed]
      response.issue shouldBe an[ComponentLockedIssue]
    }

    runOn(member1) {
      val obsId            = ObsId("Obs001")
      val cmdResponseProbe = TestProbe[CommandResponse]

      // spawn single assembly running in Standalone mode in jvm-2
      val wiring        = FrameworkWiring.make(system, locationService)
      val sequencerConf = ConfigFactory.load("command/commanding_assembly.conf")
      Await.result(Standalone.spawn(sequencerConf, wiring), 5.seconds)
      enterBarrier("spawned")

      // resolve assembly running in jvm-3 and send setup command expecting immediate command completion response
      val assemblyLocF = locationService.resolve(AkkaConnection(ComponentId("Assembly", ComponentType.Assembly)), 5.seconds)
      val assemblyRef  = Await.result(assemblyLocF, 10.seconds).map(_.componentRef()).get

      val assemblySetup = Setup(obsId, immediateCmdPrefix)
      val runId         = assemblySetup.runId

      assemblyRef ! Submit(assemblySetup, cmdResponseProbe.ref)
      cmdResponseProbe.expectMsg(5.seconds, Completed(runId))

      // short running command
      val commandResponse =
        Await.result(assemblyRef.ask[CommandResponse](Submit(Setup(obsId, invalidCmdPrefix), _)), timeout.duration)

      commandResponse shouldBe a[Invalid]

      // long running command which does not use matcher
      val longCommandResponse = Await.result(
        assemblyRef.ask[CommandResponse](Submit(Setup(obsId, acceptedCmdPrefix), _)).flatMap {
          case _: Accepted ⇒ assemblyRef.ask[CommandResponse](CommandResponseManagerMessage.Subscribe(runId, _))
          case x           ⇒ Future.successful(x)
        },
        timeout.duration
      )

      longCommandResponse shouldBe Completed(runId)

      // long running command which uses matcher

      enterBarrier("immediate-setup-complete")

      // acquire lock on assembly
      val token             = UUID.randomUUID().toString
      val lockResponseProbe = TestProbe[LockingResponse]
      assemblyRef ! Lock(lockPrefix.prefix, token, lockResponseProbe.ref)
      enterBarrier("assembly-locked")
    }

    runOn(member2) {
      // spawn container having assembly and hcd running in jvm-3
      val wiring        = FrameworkWiring.make(system, locationService)
      val containerConf = ConfigFactory.load("command/container.conf")
      Await.result(Container.spawn(containerConf, wiring), 5.seconds)
      enterBarrier("spawned")
      enterBarrier("immediate-setup-complete")
      enterBarrier("assembly-locked")
    }

    enterBarrier("end")
  }
}
