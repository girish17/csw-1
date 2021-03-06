package csw.params.extensions

import enumeratum.{Enum, EnumEntry}
import play.api.libs.json.{Format, Json, Writes}

object Formats {
  def of[T](implicit x: Format[T]): Format[T] = x

  implicit class MappableFormat[A](format: Format[A]) {
    def bimap[B](to: B => A, from: A => B): Format[B] = Format[B](
      format.map(from),
      Writes[B](x => Json.toJson(to(x))(format))
    )
  }

  implicit def enumFormat[T <: EnumEntry: Enum]: Format[T] =
    Formats.of[String].bimap[T](_.entryName, implicitly[Enum[T]].withNameInsensitive)
}
