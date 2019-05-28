package csw.framework.javadsl

import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.scaladsl
import csw.alarm.client.internal.JAlarmServiceImpl
import csw.command.client.messages.TopLevelActorMessage
import csw.config.client.javadsl.JConfigClientFactory
import csw.event.client.internal.commons.EventServiceAdapter
import csw.framework.models.{CswContext, JCswContext}
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.client.extensions.LocationServiceExt.RichLocationService

/**
 * Base class for the factory for creating the behavior representing a component actor
 */
abstract class JComponentBehaviorFactory extends ComponentBehaviorFactory() {

  protected def handlers(ctx: scaladsl.ActorContext[TopLevelActorMessage], cswCtx: CswContext): ComponentHandlers = {
    import cswCtx._
    import ctx.executionContext
    jHandlers(
      ctx.asJava,
      JCswContext(
        locationService.asJava,
        EventServiceAdapter.asJava(eventService),
        new JAlarmServiceImpl(alarmService),
        timeServiceScheduler,
        loggerFactory.asJava,
        JConfigClientFactory.clientApi(ctx.system, locationService.asJava),
        commandResponseManager,
        currentStatePublisher,
        componentInfo
      )
    )
  }

  protected[framework] def jHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: JCswContext): JComponentHandlers
}
