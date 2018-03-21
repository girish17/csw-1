package csw.framework.components.assembly

import akka.actor.typed.scaladsl.ActorContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers, CurrentStatePublisher}
import csw.common.framework.ComponentInfo
import csw.common.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory

//#component-factory
class AssemblyComponentBehaviorFactory extends ComponentBehaviorFactory {
  protected override def handlers(
      ctx: ActorContext[TopLevelActorMessage],
      componentInfo: ComponentInfo,
      commandResponseManager: CommandResponseManager,
      currentStatePublisher: CurrentStatePublisher,
      locationService: LocationService,
      loggerFactory: LoggerFactory
  ): ComponentHandlers =
    new AssemblyComponentHandlers(
      ctx,
      componentInfo,
      commandResponseManager,
      currentStatePublisher,
      locationService,
      loggerFactory
    )
}
//#component-factory
