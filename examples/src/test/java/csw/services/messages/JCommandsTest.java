package csw.services.messages;

import csw.common.commands.CommandName;
import csw.common.commands.Observe;
import csw.common.commands.Setup;
import csw.common.commands.Wait;
import csw.common.javadsl.JSubsystem;
import csw.common.javadsl.JUnits;
import csw.common.params.formats.JavaJsonSupport;
import csw.common.params.generics.JKeyTypes;
import csw.common.params.generics.Key;
import csw.common.params.generics.Parameter;
import csw.common.params.models.ArrayData;
import csw.common.params.models.MatrixData;
import csw.common.params.models.ObsId;
import csw.common.params.models.Prefix;
import org.junit.Assert;
import org.junit.Test;
import play.api.libs.json.JsValue;
import play.api.libs.json.Json;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class JCommandsTest {
    //#obsid
    ObsId obsId = new ObsId("Obs001");
    //#obsid

    @Test
    public void showUsageOfUtilityFunctions() {
        //#prefix
        //using constructor, supplying subsystem and prefix both
        Prefix prefix1 = new Prefix(JSubsystem.NFIRAOS, "nfiraos.ncc.trombone");

        //just by supplying prefix
        Prefix prefix2 = new Prefix("tcs.mobie.blue.filter");

        //invalid prefix string that cant be mapped to a valid subsystem,
        // will automatically get Subsystem.BAD
        Prefix badPrefix = new Prefix("abcdefgh");
        //#prefix

        //validations
        Assert.assertTrue(prefix1.subsystem() == JSubsystem.NFIRAOS);
        Assert.assertTrue(prefix2.subsystem() == JSubsystem.TCS);
        Assert.assertTrue(badPrefix.subsystem() == JSubsystem.BAD);
    }

    @Test
    public void showUsageOfSetupCommand() {
        //#setup
        //keys
        Key<Integer> k1 = JKeyTypes.IntKey().make("encoder");
        Key<String> k2 = JKeyTypes.StringKey().make("stringThing");
        Key<Integer> k2bad = JKeyTypes.IntKey().make("missingKey");
        Key<Integer> k3 = JKeyTypes.IntKey().make("filter");
        Key<Float> k4 = JKeyTypes.FloatKey().make("correction");

        //prefix
        String prefixName = "wfos.red.detector";

        //parameters
        Parameter<Integer> i1 = k1.set(22);
        Parameter<String> i2 = k2.set("A");

        //create setup, add sequentially using add
        Setup sc1 = new Setup(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1).add(i2);

        //access keys
        Boolean k1Exists = sc1.exists(k1); //true

        //access parameters
        Optional<Parameter<Integer>> optParam1 = sc1.jGet(k1); //present
        Optional<Parameter<Integer>> optK2Bad = sc1.jGet(k2bad); //absent

        //add more than one parameters, using madd
        Setup sc2 = sc1.madd(k3.set(1, 2, 3, 4).withUnits(JUnits.day), k4.set(1.0f, 2.0f));
        int paramSize = sc2.size();

        //add binary payload
        Key<Byte> byteKey1 = JKeyTypes.ByteKey().make("byteKey1");
        Key<Byte> byteKey2 = JKeyTypes.ByteKey().make("byteKey2");
        Byte[] bytes1 = {10, 20};
        Byte[] bytes2 = {30, 40};

        Parameter<Byte> b1 = byteKey1.set(bytes1);
        Parameter<Byte> b2 = byteKey2.set(bytes2);

        Setup sc3 = new Setup(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(b1).add(b2);

        //remove a key
        Setup sc4 = sc3.remove(b1);

        //list all keys
        java.util.List<String> allKeys = sc4.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());
        //#setup

        //validations
        Assert.assertTrue(k1Exists);
        Assert.assertTrue(optParam1.isPresent());
        Assert.assertTrue(!optK2Bad.isPresent());
        Assert.assertTrue(paramSize == 4);
        Assert.assertTrue(sc3.size() == 2);
        Assert.assertTrue(sc4.size() == 1);
        Assert.assertTrue(allKeys.size() == 1);
    }

    @Test
    public void showUsageOfObserveCommand() {
        //#observe
        //keys
        Key<Boolean> k1 = JKeyTypes.BooleanKey().make("repeat");
        Key<Integer> k2 = JKeyTypes.IntKey().make("expTime");
        Key<Integer> k2bad = JKeyTypes.IntKey().make("missingKey");
        Key<Integer> k3 = JKeyTypes.IntKey().make("filter");
        Key<Instant> k4 = JKeyTypes.TimestampKey().make("creation-time");

        //prefix
        String prefixName = "wfos.red.detector";

        //parameters
        Boolean[] boolArray = {true, false, true, false};
        Parameter<Boolean> i1 = k1.set(boolArray);
        Parameter<Integer> i2 = k2.set(1, 2, 3, 4);

        //create Observe, add sequentially using add
        Observe oc1 = new Observe(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1).add(i2);

        //access parameters
        Optional<Parameter<Boolean>> k1Param = oc1.jGet(k1); //present
        java.util.List<Boolean> values = k1Param.get().jValues();

        //access parameters
        Optional<Parameter<ArrayData<Float>>> k2BadParam = oc1.jGet(k2bad.keyName(), JKeyTypes.FloatArrayKey());

        //add more than one parameters, using madd
        Observe oc2 = oc1.madd(k3.set(1, 2, 3, 4).withUnits(JUnits.day), k4.set(Instant.now()));
        int paramSize = oc2.size();

        //update existing key with set
        Integer[] intArray = {5, 6, 7, 8};
        Observe oc3 = oc1.add(k2.set(intArray));

        //remove a key
        Observe oc4 = oc2.remove(k4);

        //list all keys
        java.util.List<String> allKeys = oc4.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());
        //#observe

        //validations
        Assert.assertTrue(k1Param.isPresent());
        Assert.assertTrue(!k2BadParam.isPresent());
        Assert.assertTrue(paramSize == 4);
        Assert.assertArrayEquals(boolArray, values.toArray());
        Assert.assertArrayEquals(intArray, (Integer[]) oc3.jGet(k2).get().values());
        Assert.assertTrue(oc4.size() == 3);
    }

    @Test
    public void showUsageOfWaitCommand() {
        //#wait
        //keys
        Key<Boolean> k1 = JKeyTypes.BooleanKey().make("repeat");
        Key<Integer> k2 = JKeyTypes.IntKey().make("expTime");
        Key<Integer> k2bad = JKeyTypes.IntKey().make("missingKey");
        Key<Integer> k3 = JKeyTypes.IntKey().make("filter");
        Key<Instant> k4 = JKeyTypes.TimestampKey().make("creation-time");

        //prefix
        String prefixName = "wfos.red.detector";

        //parameters
        Boolean[] boolArray = {true, false, true, false};
        Parameter<Boolean> i1 = k1.set(boolArray);
        Parameter<Integer> i2 = k2.set(1, 2, 3, 4);

        //create Wait, add sequentially using add
        Wait wc1 = new Wait(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1).add(i2);

        //access parameters using jGet
        Optional<Parameter<Boolean>> k1Param = wc1.jGet(k1); //present
        java.util.List<Boolean> values = k1Param.get().jValues();

        //access parameters
        Optional<Parameter<ArrayData<Float>>> k2BadParam = wc1.jGet("absentKeyHere", JKeyTypes.FloatArrayKey());

        //add more than one parameters, using madd
        Wait wc2 = wc1.madd(k3.set(1, 2, 3, 4).withUnits(JUnits.day), k4.set(Instant.now()));
        int paramSize = wc2.size();

        //update existing key with set
        Integer[] intArray = {5, 6, 7, 8};
        Wait wc3 = wc1.add(k2.set(intArray));

        //remove a key
        Wait wc4 = wc2.remove(k4);

        //list all keys
        java.util.List<String> allKeys = wc4.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());
        //#wait

        //validations
        Assert.assertTrue(k1Param.isPresent());
        Assert.assertTrue(!k2BadParam.isPresent());
        Assert.assertTrue(paramSize == 4);
        Assert.assertArrayEquals(boolArray, values.toArray());
        Assert.assertArrayEquals(intArray, (Integer[]) wc3.jGet(k2).get().values());
        Assert.assertTrue(wc4.size() == 3);
    }

    @Test
    public void showJsonSerialization() {
        //#json-serialization
        //key
        Key<MatrixData<Double>> k1 = JKeyTypes.DoubleMatrixKey().make("myMatrix");

        //values
        Double[][] doubles = {{1.0, 2.0, 3.0}, {4.1, 5.1, 6.1}, {7.2, 8.2, 9.2}};
        MatrixData<Double> m1 = MatrixData.fromJavaArrays(Double.class, doubles);

        //parameter
        Parameter<MatrixData<Double>> i1 = k1.set(m1);

        String prefixName = "wfos.blue.filter";

        //commands
        Setup sc = new Setup(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1);
        Observe oc = new Observe(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1);
        Wait wc = new Wait(new Prefix(prefixName), new CommandName("move"), Optional.of(obsId)).add(i1);

        //json support - write
        JsValue scJson = JavaJsonSupport.writeSequenceCommand(sc);
        JsValue ocJson = JavaJsonSupport.writeSequenceCommand(oc);
        JsValue wcJson = JavaJsonSupport.writeSequenceCommand(wc);

        //optionally prettify
        String str = Json.prettyPrint(scJson);

        //construct command from string
        Setup sc1 = JavaJsonSupport.readSequenceCommand(Json.parse(str));
        Observe oc1 = JavaJsonSupport.readSequenceCommand(ocJson);
        Wait wc1 = JavaJsonSupport.readSequenceCommand(wcJson);
        //#json-serialization

        Assert.assertEquals(sc, sc1);
        Assert.assertEquals(oc, oc1);
        Assert.assertEquals(wc, wc1);
    }

    @Test
    public void showUniqueKeyConstraintExample() {
        //#unique-key
        //keys
        Key<Integer> encoderKey = JKeyTypes.IntKey().make("encoder");
        Key<Integer> filterKey = JKeyTypes.IntKey().make("filter");
        Key<Integer> miscKey = JKeyTypes.IntKey().make("misc.");

        //prefix
        String prefix = "wfos.blue.filter";

        //params
        Parameter<Integer> encParam1 = encoderKey.set(1);
        Parameter<Integer> encParam2 = encoderKey.set(2);
        Parameter<Integer> encParam3 = encoderKey.set(3);

        Parameter<Integer> filterParam1 = filterKey.set(1);
        Parameter<Integer> filterParam2 = filterKey.set(2);
        Parameter<Integer> filterParam3 = filterKey.set(3);

        Parameter<Integer> miscParam1 = miscKey.set(100);

        //Setup command with duplicate key via madd
        Setup setup = new Setup(new Prefix(prefix), new CommandName("move"), Optional.of(obsId)).madd(
                encParam1,
                encParam2,
                encParam3,
                filterParam1,
                filterParam2,
                filterParam3);
        //four duplicate keys are removed; now contains one Encoder and one Filter key
        List<String> uniqueKeys1 = setup.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());

        //try adding duplicate keys via add + madd
        Setup changedSetup = setup.add(encParam3).madd(filterParam1, filterParam2, filterParam3);
        //duplicate keys will not be added. Should contain one Encoder and one Filter key
        List<String> uniqueKeys2 = changedSetup.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());

        //miscKey(unique) will be added; encoderKey(duplicate) will not be added
        Setup finalSetUp = setup.madd(miscParam1, encParam1);
        //now contains encoderKey, filterKey, miscKey
        List<String> uniqueKeys3 = finalSetUp.jParamSet().stream().map(Parameter::keyName).collect(Collectors.toList());
        //#unique-key

        //validations
        Assert.assertEquals(new HashSet<>(uniqueKeys1), new HashSet<>(Arrays.asList(encoderKey.keyName(), filterKey.keyName())));
        Assert.assertEquals(new HashSet<>(uniqueKeys2), new HashSet<>(Arrays.asList(encoderKey.keyName(), filterKey.keyName())));
        Assert.assertEquals(new HashSet<>(uniqueKeys3), new HashSet<>(Arrays.asList(encoderKey.keyName(), filterKey.keyName(), miscKey.keyName())));
    }
}
