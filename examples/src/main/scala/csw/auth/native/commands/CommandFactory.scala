package csw.auth.native.commands
import akka.actor.ActorSystem
import csw.aas.native.api.NativeAppAuthAdapter

// #command-factory
object CommandFactory {
  def makeCommand(adapter: NativeAppAuthAdapter, args: Array[String])(implicit actorSystem: ActorSystem): Option[AppCommand] = {
    args match {
      case Array("login")          => Some(new LoginCommand(adapter))
      case Array("logout")         => Some(new LogoutCommand(adapter))
      case Array("read")           => Some(new ReadCommand)
      case Array("write", content) => Some(new WriteCommand(adapter, content))
      case _ =>
        println("invalid or no command\nvalid commands are: login, logout, read & write")
        None
    }
  }
}
// #command-factory
