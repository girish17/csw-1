package csw.framework.internal.container

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import csw.framework.internal.supervisor.SupervisorInfoFactory
import csw.framework.models.ContainerInfo
import csw.common.scaladsl.ContainerActorMessage
import csw.services.location.scaladsl.{LocationService, RegistrationFactory}
import csw.services.logging.scaladsl.LoggerFactory

/**
 * Factory for creating [[akka.actor.typed.scaladsl.Behaviors.MutableBehavior]] of a container component
 */
private[framework] object ContainerBehaviorFactory {
  def behavior(
      containerInfo: ContainerInfo,
      locationService: LocationService,
      registrationFactory: RegistrationFactory
  ): Behavior[ContainerActorMessage] = {
    val supervisorFactory = new SupervisorInfoFactory(containerInfo.name)
    val loggerFactory     = new LoggerFactory(containerInfo.name)
    Behaviors.mutable(
      ctx ⇒ new ContainerBehavior(ctx, containerInfo, supervisorFactory, registrationFactory, locationService, loggerFactory)
    )
  }
}
