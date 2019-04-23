package csw.event.client.pb

import csw.params.core.generics.KeyType
import csw.params.core.generics.KeyType.IntMatrixKey
import csw.params.core.models._
import csw.params.javadsl.JKeyType
import csw.time.core.models.{TAITime, UTCTime}
import csw_protobuf.models._
import csw_protobuf.parameter.PbParameter
import csw_protobuf.parameter_types._
import org.scalatest.{FunSuite, Matchers}

// DEOPSCSW-297: Merge protobuf branch in master
class PbParameterTest extends FunSuite with Matchers {

  test("should able to parse PbParameter with multiple values") {
    val parameter: PbParameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.centimeter)
      .withIntItems(IntItems().addValues(1, 2))

    val pbParam: PbParameter       = PbParameter.parseFrom(parameter.toByteArray)
    val parsedPbParam: PbParameter = PbParameter.parseFrom(pbParam.toByteString.toByteArray)

    pbParam shouldBe parsedPbParam
  }

  test("should able to parse PbParameter with sequence of values") {
    val parameter: PbParameter = PbParameter()
      .withName("encoder")
      .withIntItems(IntItems().withValues(Array(1, 2, 3, 4)))

    val parameter1: PbParameter = PbParameter.parseFrom(parameter.toByteArray)
    val parsedParameter         = PbParameter.parseFrom(parameter1.toByteString.toByteArray)

    parameter shouldEqual parsedParameter
  }

  test("should able to create Boolean Items") {
    val booleanItems: BooleanItems = BooleanItems().addValues(true).addValues(false)
    val parsedBooleanItems         = BooleanItems.parseFrom(booleanItems.toByteString.toByteArray)
    booleanItems.values shouldBe parsedBooleanItems.values
  }

  // DEOPSCSW-661: Create UTCTimeKey and TAITimeKey replacing TimestampKey in Protobuf parameters
  test("should able to create PbParameter with UTCTime items") {
    val now = UTCTime.now()
    val parameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.second)
      .withKeyType(KeyType.UTCTimeKey)
      .withUtcTimeItems(UTCTimeItems(Array(now)))

    parameter.name shouldBe "encoder"
    parameter.units shouldBe Units.second
    parameter.keyType shouldBe KeyType.UTCTimeKey
    parameter.getUtcTimeItems.values shouldBe List(now)
  }

  // DEOPSCSW-661: Create UTCTimeKey and TAITimeKey replacing TimestampKey in Protobuf parameters
  test("should able to create PbParameter with TAITime items") {
    val now = TAITime.now()
    val parameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.second)
      .withKeyType(KeyType.TAITimeKey)
      .withTaiTimeItems(TAITimeItems(Array(now)))

    parameter.name shouldBe "encoder"
    parameter.units shouldBe Units.second
    parameter.keyType shouldBe KeyType.TAITimeKey
    parameter.getTaiTimeItems.values shouldBe List(now)
  }

  test("should able to create PbParameter with Byte items") {
    val parameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.second)
      .withKeyType(KeyType.UTCTimeKey)
      .withByteItems(ByteItems(Array(1, 2, 3, 4)))

    parameter.name shouldBe "encoder"
    parameter.units shouldBe Units.second
    parameter.keyType shouldBe KeyType.UTCTimeKey
    parameter.getByteItems.values shouldBe Seq(1, 2, 3, 4)
  }

  test("should able to create PbParameter with Choice items") {
    val strings: Seq[Choice] = Seq("a345", "b234", "c567", "d890")
    val choices              = ChoiceItems().withValues(strings.toArray)
    val parameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.second)
      .withKeyType(KeyType.UTCTimeKey)
      .withChoiceItems(choices)

    parameter.name shouldBe "encoder"
    parameter.units shouldBe Units.second
    parameter.keyType shouldBe KeyType.UTCTimeKey
  }

  test("should able to create PbParameter with int items only when KeyType is Int") {
    val parameter = PbParameter()
      .withName("encoder")
      .withUnits(Units.second)
      .withKeyType(KeyType.IntKey)
      .withCharItems(CharItems().withValues(Array('a', 'b')))
      .withIntItems(IntItems().addValues(1))

    parameter.name shouldBe "encoder"
    parameter.units shouldBe Units.second
    parameter.keyType shouldBe KeyType.IntKey
    parameter.getIntItems.values shouldBe List(1)
    parameter.getCharItems.values shouldBe List.empty
  }

  test("should able to create PbParameter and compare with Parameter for Int and String Key") {
    val key   = KeyType.IntKey.make("encoder")
    val param = key.set(1, 2, 3, 4)

    val pbParam = PbParameter()
      .withName("encoder")
      .withUnits(Units.angstrom)
      .withIntItems(IntItems().addValues(1, 2, 3, 4))

    val items: IntItems = pbParam.items.value.asInstanceOf[IntItems]
    items.values shouldBe Seq(1, 2, 3, 4)

    val parsedPbParam = PbParameter.parseFrom(pbParam.toByteArray)

    pbParam shouldBe parsedPbParam
    parsedPbParam should not be param

    val key2   = KeyType.StringKey.make("encoder")
    val param2 = key2.set("abc", "xyc")

    val pbParam2 = PbParameter()
      .withName("encoder")
      .withStringItems(StringItems().addValues("abc", "xyz"))
    val parsedPbParam2 = PbParameter.parseFrom(pbParam2.toByteArray)

    pbParam2 shouldEqual parsedPbParam2
    parsedPbParam2 should not be param2
  }

  test("should able to create PbParameter with ArrayItems") {
    val array1 = Array(1000, 2000, 3000)
    val array2 = Array(-1, -2, -3)
    val parameter: PbParameter = PbParameter()
      .withName("encoder")
      .withIntArrayItems(IntArrayItems().addValues(array1, array2))

    val values = parameter.getIntArrayItems.values
    values.head shouldBe ArrayData.fromArray(array1)
    values.tail.head shouldBe ArrayData.fromArray(array2)
  }

  test("should able to create PbParameter with MatrixItems") {
    val matrixItems: IntMatrixItems = IntMatrixItems().addValues(
      MatrixData.fromArrays(Array(1, 2, 3), Array(6, 7)),
      MatrixData.fromArrays(Array(11, 12, 13), Array(16, 17))
    )

    matrixItems.values.head.values.head.head shouldBe 1
  }

  test("should able to change the type from/to PbParameter to/from Parameter for IntKey") {
    val key         = KeyType.IntKey.make("encoder")
    val param       = key.set(1, 2, 3, 4)
    val mapper      = TypeMapperSupport.parameterTypeMapper[Int]
    val mappedParam = mapper.toCustom(mapper.toBase(param))

    param shouldEqual mappedParam
  }

  // DEOPSCSW-661: Create UTCTimeKey and TAITimeKey replacing TimestampKey in Protobuf parameters
  test("should able to change the type from/to PbParameter to/from Parameter for UTCTimeKey") {
    val mapper      = TypeMapperSupport.parameterTypeMapper2
    val key         = KeyType.UTCTimeKey.make("utcTimeKey")
    val param       = key.set(UTCTime.now())
    val mappedParam = mapper.toCustom(mapper.toBase(param))

    param shouldEqual mappedParam
  }

  // DEOPSCSW-661: Create UTCTimeKey and TAITimeKey replacing TimestampKey in Protobuf parameters
  test("should able to change the type from/to PbParameter to/from Parameter for TAITimeKey") {
    val mapper      = TypeMapperSupport.parameterTypeMapper2
    val key         = KeyType.TAITimeKey.make("taiTimeKey")
    val param       = key.set(TAITime.now())
    val mappedParam = mapper.toCustom(mapper.toBase(param))

    param shouldEqual mappedParam
  }

  test("should able to change the type from/to PbParameter to/from Parameter for IntArrayKey") {
    val mapper      = TypeMapperSupport.parameterTypeMapper2
    val key         = KeyType.IntArrayKey.make("blah")
    val param       = key.set(ArrayData.fromArray(1, 2, 3))
    val mappedParam = mapper.toCustom(mapper.toBase(param))

    param shouldEqual mappedParam
  }

  test("should able to change the type from/to PbParameter to/from Parameter for IntMatrixKey") {
    val mapper      = TypeMapperSupport.parameterTypeMapper2
    val key         = KeyType.IntMatrixKey.make("blah")
    val param       = key.set(MatrixData.fromArrays(Array(1, 2, 3), Array(6, 7)))
    val mappedParam = mapper.toCustom(mapper.toBase(param))

    param shouldEqual mappedParam
    mappedParam.keyType shouldBe IntMatrixKey
  }

  test("should able to change the type from/to PbParameter to/from Parameter using java api") {
    val key         = JKeyType.IntKey.make("encoder")
    val param       = key.set(1, 2, 3, 4)
    val mapper      = TypeMapperSupport.parameterTypeMapper2
    val parsedParam = mapper.toCustom(mapper.toBase(param))

    parsedParam shouldEqual param
  }

  test("should able to change the type from/to PbParameter to/from Parameter for RaDec") {
    PbRaDec()
    PbRaDec.defaultInstance

    PbRaDec().withRa(10).withDec(32)
    PbRaDec().withRa(10).withDec(321)

    val raDec = RaDec(1, 2)
    val param = KeyType.RaDecKey.make("Asd").set(raDec)

    val pbParameter = TypeMapperSupport.parameterTypeMapper2.toBase(param)
    pbParameter.toByteArray

    param.keyName shouldBe pbParameter.name
    param.units shouldBe pbParameter.units
    param.keyType shouldBe pbParameter.keyType
    param.items shouldBe pbParameter.getRaDecItems.values
  }
}
