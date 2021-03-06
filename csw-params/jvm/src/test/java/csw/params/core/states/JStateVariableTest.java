package csw.params.core.states;

import csw.params.commands.CommandName;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import org.junit.Assert;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.util.*;

// DEOPSCSW-183: Configure attributes and values
// DEOPSCSW-185: Easy to Use Syntax/Api
public class JStateVariableTest extends JUnitSuite {

    private final Key<Integer> encoderIntKey = JKeyType.IntKey().make("encoder");
    private final Key<String> epochStringKey = JKeyType.StringKey().make("epoch");
    private final Key<Integer> epochIntKey = JKeyType.IntKey().make("epoch");

    private final Parameter<Integer> encoderParam = encoderIntKey.set(22, 33);
    private final Parameter<String> epochStringParam = epochStringKey.set("A", "B");

    private final String prefix = "wfos.red.detector";

    @Test
    public void shouldAbleToCreateCurrentState() {
        CurrentState currentState = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam).add(epochStringParam);

        // typeName and prefix
        Assert.assertEquals(CurrentState.class.getSimpleName(), currentState.typeName());
        Assert.assertEquals(new Prefix(prefix), currentState.prefix());

        // exists
        Assert.assertTrue(currentState.exists(epochStringKey));
        Assert.assertFalse(currentState.exists(epochIntKey));

        // jParamSet
        Set<Parameter<?>> expectedParamSet = Set.of(encoderParam, epochStringParam);
        Assert.assertEquals(expectedParamSet, currentState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateCurrentStateFromSetup() {
        Prefix source = new Prefix(prefix);
        Setup setup = new Setup(source, new CommandName("move"), Optional.of(new ObsId("obsId"))).add(encoderParam).add(epochStringParam);
        CurrentState currentState = new CurrentState(new StateName("testStateName"), setup);

        // typeName and prefix
        Assert.assertEquals(CurrentState.class.getSimpleName(), currentState.typeName());
        Assert.assertEquals(source, currentState.prefix());

        // exists
        Assert.assertTrue(currentState.exists(epochStringKey));
        Assert.assertFalse(currentState.exists(epochIntKey));

        // jParamSet
        Set<Parameter<?>> expectedParamSet = Set.of(encoderParam, epochStringParam);
        Assert.assertEquals(expectedParamSet, currentState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateDemandState() {
        DemandState demandState = new DemandState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam).add(epochStringParam);

        // typeName and prefix
        Assert.assertEquals(DemandState.class.getSimpleName(), demandState.typeName());
        Assert.assertEquals(new Prefix(prefix), demandState.prefix());

        // exists
        Assert.assertTrue(demandState.exists(epochStringKey));
        Assert.assertFalse(demandState.exists(epochIntKey));

        // jParamSet
        Set<Parameter<?>> expectedParamSet = Set.of(encoderParam, epochStringParam);
        Assert.assertEquals(expectedParamSet, demandState.jParamSet());
    }

    @Test
    public void shouldAbleToCreateDemandStateFromSetup() {
        Prefix source = new Prefix(prefix);
        Setup setup = new Setup(source, new CommandName("move"), Optional.of(new ObsId("obsId"))).add(encoderParam).add(epochStringParam);
        DemandState demandState = new DemandState(new StateName("testStateName"), setup);

        // typeName and prefix
        Assert.assertEquals(DemandState.class.getSimpleName(), demandState.typeName());
        Assert.assertEquals(source, demandState.prefix());

        // exists
        Assert.assertTrue(demandState.exists(epochStringKey));
        Assert.assertFalse(demandState.exists(epochIntKey));

        // jParamSet
        Set<Parameter<?>> expectedParamSet = Set.of(encoderParam, epochStringParam);
        Assert.assertEquals(expectedParamSet, demandState.jParamSet());
    }

    @Test
    public void shouldAbleToMatchWithDefaultMatcher() {
        CurrentState currentState = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam).add(epochStringParam);
        DemandState demandState = new DemandState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam).add(epochStringParam);

        Assert.assertTrue(StateVariable.defaultMatcher(demandState, currentState));
    }

    @Test
    public void shouldAbleToCreateCurrentStatesUsingVargs() {
        CurrentState currentState1 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam);
        CurrentState currentState2 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(epochStringParam);
        CurrentState currentState3 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(epochStringParam);
        List<CurrentState> expectedCurrentStates = Arrays.asList(currentState1, currentState2, currentState3);

        CurrentStates currentStates = StateVariable.createCurrentStates(currentState1, currentState2, currentState3);

        List<CurrentState> actualCurrentStates = currentStates.jStates();
        Assert.assertEquals(expectedCurrentStates, actualCurrentStates);
    }

    @Test
    public void shouldAbleToCreateCurrentStatesUsingList() {
        CurrentState currentState1 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(encoderParam);
        CurrentState currentState2 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(epochStringParam);
        CurrentState currentState3 = new CurrentState(new Prefix(prefix), new StateName("testStateName")).add(epochStringParam);
        List<CurrentState> expectedCurrentStates = Arrays.asList(currentState1, currentState2, currentState3);

        CurrentStates currentStates = StateVariable.createCurrentStates(expectedCurrentStates);

        List<CurrentState> actualCurrentStates = currentStates.jStates();
        Assert.assertEquals(expectedCurrentStates, actualCurrentStates);
    }
}
