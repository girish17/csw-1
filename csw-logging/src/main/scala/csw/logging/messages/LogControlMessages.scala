package csw.logging.messages

import akka.actor.typed.ActorRef
import csw.serializable.TMTSerializable
import csw.logging.internal.LoggingLevels.Level
import csw.logging.models.LogMetadata

// Parent trait for Messages which will be send to components for interacting with its logging system
sealed trait LogControlMessages extends TMTSerializable

// Message to get Logging configuration metadata of the receiver
case class GetComponentLogMetadata(componentName: String, replyTo: ActorRef[LogMetadata]) extends LogControlMessages

// Message to change the log level of any component
case class SetComponentLogLevel(componentName: String, logLevel: Level) extends LogControlMessages
