package csw.params.core.models
import csw.params.core.models.Prefix.SEPARATOR
import csw.serializable.TMTSerializable
import play.api.libs.json._

/**
 * A top level key for a parameter set: combination of subsystem and the subsystem's prefix
 * Eg. tcs.filter.wheel
 *
 * @param prefix    the subsystem's prefix
 */
case class Prefix(prefix: String) extends TMTSerializable {
  val subsystem: Subsystem = {
    require(prefix != null)
    Subsystem.withNameOption(prefix.splitAt(prefix.indexOf(SEPARATOR))._1).getOrElse(Subsystem.BAD)
  }
}

object Prefix {
  private val SEPARATOR = "."

  implicit val format: Format[Prefix] = new Format[Prefix] {
    override def writes(obj: Prefix): JsValue           = JsString(obj.prefix)
    override def reads(json: JsValue): JsResult[Prefix] = JsSuccess(Prefix(json.as[String]))
  }
}
