package csw.messages.params.pb

import csw.messages.params.formats.{EnumJsonSupport, JavaFormats, JsonSupport, WrappedArrayProtocol}
import csw.messages.params.generics.Parameter
import spray.json.{DefaultJsonProtocol, JsonFormat}

import scala.reflect.ClassTag

object TestJavaFormats
    extends JsonSupport
    with DefaultJsonProtocol
    with JavaFormats
    with EnumJsonSupport
    with WrappedArrayProtocol {
  def paramFormat[T: JsonFormat: ClassTag: ItemsFactory]: JsonFormat[Parameter[T]] =
    implicitly[JsonFormat[Parameter[T]]]
}