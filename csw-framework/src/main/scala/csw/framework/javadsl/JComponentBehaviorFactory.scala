package csw.framework.javadsl

import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.scaladsl
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers, CurrentStatePublisher}
import csw.common.framework.ComponentInfo
import csw.common.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.CommandResponseManager
import csw.services.location.javadsl.ILocationService
import csw.services.location.scaladsl.LocationService
import csw.services.logging.javadsl.JLoggerFactory
import csw.services.logging.scaladsl.LoggerFactory

/**
 * Base class for the factory for creating the behavior representing a component actor
 */
abstract class JComponentBehaviorFactory extends ComponentBehaviorFactory() {

  protected def handlers(
      ctx: scaladsl.ActorContext[TopLevelActorMessage],
      componentInfo: ComponentInfo,
      commandResponseManager: CommandResponseManager,
      currentStatePublisher: CurrentStatePublisher,
      locationService: LocationService,
      loggerFactory: LoggerFactory
  ): ComponentHandlers =
    jHandlers(
      ctx.asJava,
      componentInfo,
      commandResponseManager,
      currentStatePublisher,
      locationService.asJava,
      loggerFactory.asJava
    )

  protected[framework] def jHandlers(
      ctx: ActorContext[TopLevelActorMessage],
      componentInfo: ComponentInfo,
      commandResponseManager: CommandResponseManager,
      currentStatePublisher: CurrentStatePublisher,
      locationService: ILocationService,
      loggerFactory: JLoggerFactory
  ): JComponentHandlers
}
