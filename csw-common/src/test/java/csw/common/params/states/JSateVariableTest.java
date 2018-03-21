package csw.common.params.states;

import csw.common.commands.CommandName;
import csw.common.commands.Setup;
import csw.common.params.generics.JKeyTypes;
import csw.common.params.generics.Key;
import csw.common.params.generics.Parameter;
import csw.common.params.models.ObsId;
import csw.common.params.models.Prefix;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

// DEOPSCSW-183: Configure attributes and values
// DEOPSCSW-185: Easy to Use Syntax/Api
public class JSateVariableTest {

    private final Key<Integer> encoderIntKey = JKeyTypes.IntKey().make("encoder");
    private final Key<String> epochStringKey = JKeyTypes.StringKey().make("epoch");
    private final Key<Integer> epochIntKey = JKeyTypes.IntKey().make("epoch");

    private final Parameter<Integer> encoderParam = encoderIntKey.set(22, 33);
    private final Parameter<String> epochStringParam = epochStringKey.set("A", "B");

    private final String prefix = "wfos.red.detector";

    @Test
    public void shouldAbleToCreateCurrentState() {
        CurrentState currentState = new CurrentState(prefix).add(encoderParam).add(epochStringParam);

        // typeName and prefix
        Assert.assertEquals(CurrentState.class.getSimpleName(), currentState.typeName());
        Assert.assertEquals(new Prefix(prefix), currentState.prefix());

        // exists
        Assert.assertTrue(currentState.exists(epochStringKey));
        Assert.assertFalse(currentState.exists(epochIntKey));

        // jParamSet
        HashSet<Parameter<?>> expectedParamSet = new HashSet<>(Arrays.asList(encoderParam, epochStringParam));
        Assert.assertEquals(expectedParamSet, currentState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateCurrentStateFromSetup() {
        Prefix source = new Prefix(prefix);
        Setup setup = new Setup(source, new CommandName("move"), Optional.of(new ObsId("obsId"))).add(encoderParam).add(epochStringParam);
        CurrentState currentState = new CurrentState(setup);

        // typeName and prefix
        Assert.assertEquals(CurrentState.class.getSimpleName(), currentState.typeName());
        Assert.assertEquals(source, currentState.prefix());

        // exists
        Assert.assertTrue(currentState.exists(epochStringKey));
        Assert.assertFalse(currentState.exists(epochIntKey));

        // jParamSet
        HashSet<Parameter<?>> expectedParamSet = new HashSet<>(Arrays.asList(encoderParam, epochStringParam));
        Assert.assertEquals(expectedParamSet, currentState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateDemandState() {
        DemandState demandState = new DemandState(prefix).add(encoderParam).add(epochStringParam);

        // typeName and prefix
        Assert.assertEquals(DemandState.class.getSimpleName(), demandState.typeName());
        Assert.assertEquals(new Prefix(prefix), demandState.prefix());

        // exists
        Assert.assertTrue(demandState.exists(epochStringKey));
        Assert.assertFalse(demandState.exists(epochIntKey));

        // jParamSet
        HashSet<Parameter<?>> expectedParamSet = new HashSet<>(Arrays.asList(encoderParam, epochStringParam));
        Assert.assertEquals(expectedParamSet, demandState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateDemandStateFromSetup() {
        Prefix source = new Prefix(prefix);
        Setup setup = new Setup(source, new CommandName("move"), Optional.of(new ObsId("obsId"))).add(encoderParam).add(epochStringParam);
        DemandState demandState = new DemandState(setup);

        // typeName and prefix
        Assert.assertEquals(DemandState.class.getSimpleName(), demandState.typeName());
        Assert.assertEquals(source, demandState.prefix());

        // exists
        Assert.assertTrue(demandState.exists(epochStringKey));
        Assert.assertFalse(demandState.exists(epochIntKey));

        // jParamSet
        HashSet<Parameter<?>> expectedParamSet = new HashSet<>(Arrays.asList(encoderParam, epochStringParam));
        Assert.assertEquals(expectedParamSet, demandState.jParamSet());
    }

    @Test
    public void shouldAbleToMatchWithDefaultMatcher() {
        CurrentState currentState = new CurrentState(prefix).add(encoderParam).add(epochStringParam);
        DemandState demandState = new DemandState(prefix).add(encoderParam).add(epochStringParam);

        Assert.assertTrue(StateVariable.defaultMatcher(demandState, currentState));
    }

    @Test
    public void shouldAbleToCreateCurrentStatesUsingVargs() {
        CurrentState currentState1 = new CurrentState(prefix).add(encoderParam);
        CurrentState currentState2 = new CurrentState(prefix).add(epochStringParam);
        CurrentState currentState3 = new CurrentState(prefix).add(epochStringParam);
        List<CurrentState> expectedCurrentStates = Arrays.asList(currentState1, currentState2, currentState3);

        CurrentStates currentStates = StateVariable.createCurrentStates(currentState1, currentState2, currentState3);

        List<CurrentState> actualCurrentStates = currentStates.jStates();
        Assert.assertEquals(expectedCurrentStates, actualCurrentStates);
    }

    @Test
    public void shouldAbleToCreateCurrentStatesUsingList() {
        CurrentState currentState1 = new CurrentState(prefix).add(encoderParam);
        CurrentState currentState2 = new CurrentState(prefix).add(epochStringParam);
        CurrentState currentState3 = new CurrentState(prefix).add(epochStringParam);
        List<CurrentState> expectedCurrentStates = Arrays.asList(currentState1, currentState2, currentState3);

        CurrentStates currentStates = StateVariable.createCurrentStates(expectedCurrentStates);

        List<CurrentState> actualCurrentStates = currentStates.jStates();
        Assert.assertEquals(expectedCurrentStates, actualCurrentStates);
    }
}
