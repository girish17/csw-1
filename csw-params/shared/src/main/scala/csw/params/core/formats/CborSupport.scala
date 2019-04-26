package csw.params.core.formats

import java.time.Instant

import csw.params.core.generics.{KeyType, Parameter}
import csw.params.core.models._
import csw.params.events.{Event, EventName, ObserveEvent, SystemEvent}
import csw.time.core.models.{TAITime, UTCTime}
import enumeratum._
import io.bullet.borer._
import io.bullet.borer.derivation.MapBasedCodecs._
import play.api.libs.json.Format

import scala.collection.mutable
import scala.reflect.ClassTag

object CborSupport extends LowPriorityCborSupport {
  implicit def paramCodec[T: Format: ClassTag: Encoder: Decoder]: Codec[Parameter[T]] = deriveCodec[Parameter[T]]
}

trait LowPriorityCborSupport {
  implicit lazy val choiceCodec: Codec[Choice] = deriveCodec[Choice]
  implicit lazy val raDecCodec: Codec[RaDec]   = deriveCodec[RaDec]

  implicit lazy val tsCodec: Codec[Timestamp] = deriveCodec[Timestamp]
  implicit lazy val insEnc: Encoder[Instant] =
    Encoder.fromCodec[Timestamp].compose[Instant](x => Timestamp(x.getEpochSecond, x.getNano))
  implicit lazy val insDec: Decoder[Instant] =
    Decoder.fromCodec[Timestamp].map[Instant](x => Instant.ofEpochSecond(x.seconds, x.nanos))

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

  implicit lazy val paramEncExistential: Encoder[Parameter[_]] = { (w: Writer, value: Parameter[_]) =>
    val encoder: Encoder[Parameter[Any]] = value.keyType.paramEncoder.asInstanceOf[Encoder[Parameter[Any]]]
    encoder.write(w, value.asInstanceOf[Parameter[Any]])
  }

  implicit lazy val paramDecExistential: Decoder[Parameter[_]] = { r: Reader =>
    r.tryReadMapHeader(4) || r.tryReadArrayHeader(4)
    r.tryReadString("keyName")
    val keyName = r.readString()
    r.tryReadString("keyType")
    val keyType = KeyType.withNameInsensitive(r.readString())
    r.tryReadString("items")
    val wa = keyType.waDecoder.read(r)
    r.tryReadString("units")
    val units = unitsCodec.decoder.read(r)
    Parameter(keyName, keyType.asInstanceOf[KeyType[Any]], wa.asInstanceOf[mutable.WrappedArray[Any]], units)
  }

  implicit lazy val structCodec: Codec[Struct] = deriveCodec[Struct]

  // ************************ EVENT CODECS ********************

  implicit lazy val idCodec: Codec[Id]               = deriveCodec[Id]
  implicit lazy val prefixCodec: Codec[Prefix]       = deriveCodec[Prefix]
  implicit lazy val eventNameCodec: Codec[EventName] = deriveCodec[EventName]

  implicit lazy val sysEventCodec: Codec[SystemEvent]  = deriveCodec[SystemEvent]
  implicit lazy val obsEventCodec: Codec[ObserveEvent] = deriveCodec[ObserveEvent]
  implicit lazy val eventCodec: Codec[Event]           = deriveCodec[Event]
}

case class Timestamp(seconds: Long, nanos: Long)
