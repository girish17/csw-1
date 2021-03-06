package csw.location

import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.testkit.TestProbe
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models._
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.location.helpers.{LSNodeSpec, TwoMembersAndSeed}
import csw.location.server.commons.CswCluster
import csw.location.server.internal.{LocationServiceFactory, ServerWiring}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.params.core.models.Prefix
import org.jboss.netty.logging.{InternalLoggerFactory, Slf4JLoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

class DetectComponentRestartTestMultiJvmNode1 extends DetectComponentRestartTest(0, "cluster")
class DetectComponentRestartTestMultiJvmNode2 extends DetectComponentRestartTest(0, "cluster")
class DetectComponentRestartTestMultiJvmNode3 extends DetectComponentRestartTest(0, "cluster")

class DetectComponentRestartTest(ignore: Int, mode: String) extends LSNodeSpec(config = new TwoMembersAndSeed, mode) {

  import config._

  // Fix to avoid 'java.util.concurrent.RejectedExecutionException: Worker has already been shutdown'
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory)

  test("should detect re-registering of new location for a connection that has crashed/gone away") {

    val akkaConnection = AkkaConnection(ComponentId("TromboneHcd", ComponentType.HCD))

    runOn(member1) {
      locationService
        .register(AkkaRegistration(akkaConnection, Prefix("nfiraos.ncc.trombone"), typedSystem.spawn(Behavior.empty, "empty")))
        .await

      enterBarrier("location-registered")
      enterBarrier("location-updated")

      Await.result(system.whenTerminated, 10.seconds)

      startNewSystem()

      val newConfig = if(sys.env.get("CLUSTER_SEEDS").isEmpty)
        config.settings.joinLocal(3552).config
      else config.settings.config

      val newSystem      = makeSystem(newConfig)
      val newTypedSystem = newSystem.toTyped.asInstanceOf[ActorSystem[SpawnProtocol]]

      val freshLocationService = mode match {
        case "http" =>
          Try(ServerWiring.make(newTypedSystem).locationHttpService.start().await) match {
            case _ => // ignore binding errors
          }
          HttpLocationServiceFactory.makeLocalClient(newTypedSystem, ActorMaterializer()(newTypedSystem))
        case "cluster" => LocationServiceFactory.withCluster(CswCluster.withSystem(newTypedSystem))
      }

      Thread.sleep(2000)

      freshLocationService
        .register(
          AkkaRegistration(
            akkaConnection,
            Prefix("nfiraos.ncc.trombone"),
            newTypedSystem.spawn(Behavior.empty, "empty")
          )
        )
        .await
      enterBarrier("member-re-registered")
    }

    runOn(seed, member2) {
      enterBarrier("location-registered")
      val testProbe  = TestProbe()
      val killSwitch = locationService.subscribe(akkaConnection, testProbe.testActor ! _)

      testProbe.expectMsgType[LocationUpdated]
      enterBarrier("location-updated")

      runOn(seed) {
        Await.result(testConductor.shutdown(member1), 10.seconds)
      }

      testProbe.expectMsgType[LocationRemoved](5.seconds)
      Thread.sleep(2000)
      enterBarrier("member-re-registered")
      testProbe.expectMsgType[LocationUpdated](5.seconds)

      killSwitch.shutdown()
    }

    enterBarrier("after-2")
  }

}
