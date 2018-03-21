package csw.common.components.framework

import csw.common.location.Connection.{HttpConnection, TcpConnection}
import csw.common.location.{ComponentId, ComponentType}
import csw.common.params.generics.GChoiceKey
import csw.common.params.generics.KeyType.ChoiceKey
import csw.common.params.models.{Choice, Choices, Prefix}

object SampleComponentState {
  val restartChoice             = Choice("Restart")
  val onlineChoice              = Choice("Online")
  val domainChoice              = Choice("Domain")
  val shutdownChoice            = Choice("Shutdown")
  val setupConfigChoice         = Choice("SetupConfig")
  val observeConfigChoice       = Choice("ObserveConfig")
  val commandValidationChoice   = Choice("CommandValidation")
  val submitCommandChoice       = Choice("SubmitCommand")
  val oneWayCommandChoice       = Choice("OneWayCommand")
  val initChoice                = Choice("Initialize")
  val offlineChoice             = Choice("Offline")
  val akkaLocationUpdatedChoice = Choice("LocationUpdated")
  val akkaLocationRemovedChoice = Choice("LocationRemoved")
  val httpLocationUpdatedChoice = Choice("HttpLocationUpdated")
  val httpLocationRemovedChoice = Choice("HttpLocationRemoved")
  val tcpLocationUpdatedChoice  = Choice("TcpLocationUpdated")
  val tcpLocationRemovedChoice  = Choice("TcpLocationRemoved")
  val prefix                    = Prefix("wfos.prog.cloudcover")
  val successPrefix             = Prefix("wfos.prog.cloudcover.success")
  val failedPrefix              = Prefix("wfos.prog.cloudcover.failure")

  val choices: Choices =
    Choices.fromChoices(
      restartChoice,
      onlineChoice,
      domainChoice,
      shutdownChoice,
      setupConfigChoice,
      observeConfigChoice,
      commandValidationChoice,
      submitCommandChoice,
      oneWayCommandChoice,
      initChoice,
      offlineChoice,
      akkaLocationUpdatedChoice,
      akkaLocationRemovedChoice,
      httpLocationUpdatedChoice,
      httpLocationRemovedChoice,
      tcpLocationUpdatedChoice,
      tcpLocationRemovedChoice
    )
  val choiceKey: GChoiceKey          = ChoiceKey.make("choiceKey", choices)
  val httpConnection: HttpConnection = HttpConnection(ComponentId("exampleHTTPService", ComponentType.Service))
  val tcpConnection: TcpConnection   = TcpConnection(ComponentId("exampleTcpService", ComponentType.Service))
}
