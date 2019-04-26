package csw.benchmark.event

import java.util.concurrent.TimeUnit

import csw.params.core.generics.{KeyType, Parameter}
import csw.params.events.SystemEvent
import csw.params.javadsl.JKeyType
import io.bullet.borer.Cbor
import org.openjdk.jmh.annotations._

// RUN using this command: csw-benchmark/jmh:run -f 1 -wi 5 -i 5 csw.benchmark.event.CborSerializationBenchmark

import csw.params.core.formats.CborSupport._
@State(Scope.Benchmark)
class CborSerializationBenchmark {

  @Benchmark
  @BenchmarkMode(Array(Mode.Throughput))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def cborThrpt(): SystemEvent = {
    val bytes: Array[Byte] = Cbor.encode(Data.event).toByteArray
    Cbor.decode(bytes).to[SystemEvent].value
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.AverageTime))
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def cborAvgTime(): SystemEvent = {
    val bytes: Array[Byte] = Cbor.encode(Data.event).toByteArray
    Cbor.decode(bytes).to[SystemEvent].value
  }

}

object BigCborTest extends App {
  val bytes: Array[Byte] = Cbor.encode(Data.event).toByteArray
  val event: SystemEvent = Cbor
    .decode(bytes)
//    .withPrintLogging()
    .to[SystemEvent]
    .value
  println(event)
  println(bytes.length)
}

object SimpleCborTest extends App {
  import csw.params.core.formats.JsonSupport._

  private val intKey                    = KeyType.IntKey.make("ints")
  private val jintKey                   = JKeyType.IntKey.make("ints")
  private val param: Parameter[Integer] = jintKey.set(5, 6, 7)
  private val param2: Parameter[Int]    = intKey.set(5, 6, 7)
//  private val params: Set[Parameter[_]] = Set(param, param2)
  val bytes: Array[Byte] = Cbor.encode(param2).toByteArray

  val result = Cbor
    .decode(bytes)
    .withPrintLogging()
    .to[Parameter[_]]
    .value
    .asInstanceOf[Parameter[Integer]]

  println(result)
}
