package csw.alarm.client;

import akka.Done;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.SpawnProtocol;
import csw.alarm.api.models.AutoRefreshSeverityMessage;
import csw.alarm.api.models.AutoRefreshSeverityMessage.AutoRefreshSeverity;
import csw.alarm.api.models.AutoRefreshSeverityMessage.CancelAutoRefresh;
import csw.alarm.api.models.Key.AlarmKey;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static csw.alarm.api.javadsl.JAlarmSeverity.Major;
import static csw.params.javadsl.JSubsystem.NFIRAOS;

public class JAlarmRefreshActorTest extends JUnitSuite {
    private ActorSystem<SpawnProtocol> typedSystem = ActorSystem.apply(SpawnProtocol.behavior(), "SpawnProtocolGuardian");

    // DEOPSCSW-507: Auto-refresh utility for component developers
    @Test
    public void should_refresh_severity() {
        AlarmKey alarmKey = new AlarmKey(NFIRAOS, "trombone", "tromboneAxisHighLimitAlarm");
        TestProbe<String> probe = TestProbe.create(typedSystem);
        String refreshMsg = "severity refreshed";

        ActorRef<AutoRefreshSeverityMessage> ref = AlarmRefreshActorFactory.jMake((key, severity) -> CompletableFuture.supplyAsync(() -> {
            probe.ref().tell(refreshMsg);
            return Done.done();
        }), Duration.ofMillis(200), typedSystem);

        ref.tell(new AutoRefreshSeverity(alarmKey, Major));
        probe.expectMessage(refreshMsg);
        probe.expectNoMessage(Duration.ofMillis(190));
        probe.expectMessage(Duration.ofMillis(50), refreshMsg);

        ref.tell(new CancelAutoRefresh(alarmKey));
        probe.expectNoMessage(Duration.ofMillis(210));
    }

}
