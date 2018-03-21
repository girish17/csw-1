package csw.common.params;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import csw.common.commands.Observe;
import csw.common.commands.Setup;
import csw.common.commands.Wait;
import csw.common.events.EventName;
import csw.common.events.SystemEvent;
import csw.common.params.generics.JKeyTypes;
import csw.common.params.generics.Key;
import csw.common.params.generics.Parameter;
import csw.common.params.models.Prefix;

import java.util.Set;

public abstract class JavaCommandHandler {

    private static final Prefix prefix = new Prefix("wfos.red.detector");

    private static final Key<Integer> encoderIntKey = JKeyTypes.IntKey().make("encoder");
    private static final Key<String> epochStringKey = JKeyTypes.StringKey().make("epoch");

    static final Parameter<Integer> encoderParam = encoderIntKey.set(55, 66);
    static final Parameter<String> epochStringParam = epochStringKey.set("Event1", "Event2");

    private static SystemEvent systemEvent = new SystemEvent(prefix, new EventName("")).add(encoderParam).add(epochStringParam);

    private JavaCommandHandler() {
    }

    public static Behavior<CommandMsg> behavior() {
        return Behaviors.immutable(CommandMsg.class)
                .onMessage(CommandMsg.class, msg -> msg.command().getClass().isAssignableFrom(Setup.class),(ctx, msg) -> {
                    Set<Parameter<?>> jParamSet = ((Setup) msg.command()).jParamSet();
                    msg.ackTo().tell(jParamSet);
                    msg.replyTo().tell(systemEvent);
                    msg.obsIdAck().tell(msg.command().jMaybeObsId());
                    return Behaviors.same();
                })
                .onMessage(CommandMsg.class, msg -> msg.command().getClass().isAssignableFrom(Wait.class),(ctx, msg) -> {
                    Set<Parameter<?>> jParamSet = ((Setup) msg.command()).jParamSet();
                    msg.ackTo().tell(jParamSet);
                    msg.obsIdAck().tell(msg.command().jMaybeObsId());
                    return Behaviors.same();
                })
                .onMessage(CommandMsg.class, msg -> msg.command().getClass().isAssignableFrom(Observe.class),(ctx, msg) -> {
                    Set<Parameter<?>> jParamSet = ((Setup) msg.command()).jParamSet();
                    msg.ackTo().tell(jParamSet);
                    msg.obsIdAck().tell(msg.command().jMaybeObsId());
                    return Behaviors.same();
                })
                .build();
    }

}


