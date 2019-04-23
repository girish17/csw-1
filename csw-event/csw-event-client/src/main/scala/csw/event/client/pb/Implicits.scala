package csw.event.client.pb

import java.time.Instant

import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import csw.time.core.models.{TAITime, TMTTime, UTCTime}
import scalapb.TypeMapper

/**
 * Type mappers for implicit conversions of data types that are not directly supported by Protobuf.
 */
object Implicits {

  /**
   * Implicit conversion of Instant(provided by Java and supported by csw) to Timestamp(supported by Protobuf)
   */
  implicit val instantMapper: TypeMapper[Timestamp, Instant] =
    TypeMapper[Timestamp, Instant] { x =>
      Instant.ofEpochSecond(x.seconds, x.nanos)
    } { x =>
      Timestamp().withSeconds(x.getEpochSecond).withNanos(x.getNano)
    }

  implicit val utcMapper: TypeMapper[Timestamp, UTCTime] = tmtTimeMapper(UTCTime(_))

  implicit val taiMapper: TypeMapper[Timestamp, TAITime] = tmtTimeMapper(TAITime(_))

  private def tmtTimeMapper[T <: TMTTime](factory: Instant ⇒ T): TypeMapper[Timestamp, T] =
    TypeMapper[Timestamp, T] { x ⇒
      factory(instantMapper.toCustom(x))
    } { x =>
      instantMapper.toBase(x.value)
    }

  /**
   * Implicit conversion of Seq[Byte](supported by csw) to ByteString(supported by Protobuf)
   */
  implicit val bytesMapper: TypeMapper[ByteString, Array[Byte]] =
    TypeMapper[ByteString, Array[Byte]](_.toByteArray)(xs ⇒ ByteString.copyFrom(xs))

  /**
   * Implicit conversion of Seq[Char](supported by csw) to String(supported by Protobuf)
   */
  implicit val charsMapper: TypeMapper[String, Array[Char]] =
    TypeMapper[String, Array[Char]](s ⇒ s.toCharArray)(xs ⇒ String.copyValueOf(xs))

  /**
   * Implicit conversion of Short(supported by csw) to Int(Protobuf doesn't support Short, hence promoted to Int)
   */
  implicit val shortMapper: TypeMapper[Int, Short] = TypeMapper[Int, Short](x ⇒ x.toShort)(x ⇒ x)
}
