package csw.services.event.internal.redis

import java.nio.ByteBuffer

import csw.messages.events.Event
import csw_protobuf.events.PbEvent
import io.lettuce.core.codec.{RedisCodec, Utf8StringCodec}

/**
 * Encodes and decodes keys as Strings and values as ProtoBuf byte equivalent of Event
 */
object PatternBasedEventServiceCodec extends RedisCodec[String, Event] {

  private lazy val utf8StringCodec = new Utf8StringCodec()

  override def encodeKey(eventKey: String): ByteBuffer = utf8StringCodec.encodeKey(eventKey)

  override def decodeKey(byteBuf: ByteBuffer): String = utf8StringCodec.decodeKey(byteBuf)

  override def encodeValue(event: Event): ByteBuffer = {
    val pbEvent = Event.typeMapper.toBase(event)
    ByteBuffer.wrap(pbEvent.toByteArray)
  }

  override def decodeValue(byteBuf: ByteBuffer): Event = {
    val bytes = new Array[Byte](byteBuf.remaining)
    byteBuf.get(bytes)
    val pbEvent = PbEvent.parseFrom(bytes)
    Event.fromPb(pbEvent)
  }
}