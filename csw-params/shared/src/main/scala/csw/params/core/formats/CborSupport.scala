package csw.params.core.formats

import java.time.Instant

import com.github.ghik.silencer.silent
import csw.params.core.generics.{KeyType, Parameter}
import csw.params.core.models._
import csw.params.events.{Event, EventName, ObserveEvent, SystemEvent}
import csw.time.core.models.{TAITime, UTCTime}
import enumeratum._
import io.bullet.borer._
import io.bullet.borer.derivation.MapBasedCodecs._
import play.api.libs.json.Format

import scala.reflect.ClassTag

object CborSupport extends LowPriorityCborSupport {
  implicit def paramEnc[T: Format: ClassTag: Encoder: Decoder]: Encoder[Parameter[T]] = {
    packCodec[T].encoder.compose[Parameter[T]](x ⇒ Pack(x.keyType.entryName, x))
  }

  implicit def paramDec[T: Format: ClassTag: Encoder: Decoder]: Decoder[Parameter[T]] = {
    packCodec[T].decoder.map[Parameter[T]](_.parameter)
  }
}

trait LowPriorityCborSupport {
  implicit lazy val choiceCodec: Codec[Choice] = deriveCodec[Choice]
  implicit lazy val raDecCodec: Codec[RaDec]   = deriveCodec[RaDec]

  implicit lazy val tsCodec: Codec[TS]       = deriveCodec[TS]
  implicit lazy val insEnc: Encoder[Instant] = Encoder.fromCodec[TS].compose[Instant](x => TS(x.getEpochSecond, x.getNano))
  implicit lazy val insDec: Decoder[Instant] = Decoder.fromCodec[TS].map[Instant](x => Instant.ofEpochSecond(x.seconds, x.nanos))

  implicit lazy val utcTimeEncoder: Codec[UTCTime] = deriveCodec[UTCTime]
  implicit lazy val taiTimeEncoder: Codec[TAITime] = deriveCodec[TAITime]

  implicit def arrayDataEnc[T: Encoder: Decoder: ClassTag]: Codec[ArrayData[T]]   = deriveCodec[ArrayData[T]]
  implicit def matrixDataEnc[T: Encoder: Decoder: ClassTag]: Codec[MatrixData[T]] = deriveCodec[MatrixData[T]]

  def enumEnc[T <: EnumEntry]: Encoder[T]       = Encoder.forString.compose[T](_.entryName)
  def enumDec[T <: EnumEntry: Enum]: Decoder[T] = Decoder.forString.map[T](implicitly[Enum[T]].withNameInsensitive)
  def enumCodec[T <: EnumEntry: Enum]: Codec[T] = Codec(enumEnc[T], enumDec[T])

  implicit lazy val unitsCodec: Codec[Units]                        = enumCodec[Units]
  implicit lazy val keyTypeCodecExistential: Codec[KeyType[_]]      = enumCodec[KeyType[_]]
  implicit def keyTypeCodec[T: Format: ClassTag]: Codec[KeyType[T]] = keyTypeCodecExistential.asInstanceOf[Codec[KeyType[T]]]

  def paramCodec[T: Format: ClassTag: Encoder: Decoder]: Codec[Parameter[T]] = deriveCodec[Parameter[T]]

  def packCodec[T: Format: ClassTag: Encoder: Decoder]: Codec[Pack[T]] = {
    @silent
    implicit val paramCodec: Codec[Parameter[T]] = deriveCodec[Parameter[T]]
    deriveCodec[Pack[T]]
  }

  implicit def paramEncExistential: Encoder[Parameter[_]] = { (w: Writer, value: Parameter[_]) =>
    val encoder: Encoder[Parameter[Any]] = value.keyType.paramEncWithKey.asInstanceOf[Encoder[Parameter[Any]]]
    encoder.write(w, value.asInstanceOf[Parameter[Any]])
  }

  implicit def paramDecExistential: Decoder[Parameter[_]] = { r: Reader =>
    r.readMapHeader()
    r.readString()
    val keyTypeName = new String(r.readTextBytes())
    val keyType     = KeyType.withNameInsensitive(keyTypeName)
    r.readString()
    r.read()(keyType.paramDecWithoutKey)
  }

  implicit def structCodec: Codec[Struct] = deriveCodec[Struct]

  // ************************ EVENT CODECS ********************

  implicit lazy val idCodec: Codec[Id]               = deriveCodec[Id]
  implicit lazy val prefixCodec: Codec[Prefix]       = deriveCodec[Prefix]
  implicit lazy val eventNameCodec: Codec[EventName] = deriveCodec[EventName]

  implicit lazy val sysEventCodec: Codec[SystemEvent]  = deriveCodec[SystemEvent]
  implicit lazy val obsEventCodec: Codec[ObserveEvent] = deriveCodec[ObserveEvent]
  implicit lazy val eventCodec: Codec[Event]           = deriveCodec[Event]
}

case class Pack[T](keyType: String, parameter: Parameter[T])
case class TS(seconds: Long, nanos: Long)
