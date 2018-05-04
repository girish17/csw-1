package csw.services.event.internal.kafka;

import akka.actor.typed.javadsl.Adapter;
import akka.stream.javadsl.Source;
import akka.testkit.typed.javadsl.TestProbe;
import csw.messages.commons.CoordinatedShutdownReasons;
import csw.messages.events.Event;
import csw.services.event.exceptions.PublishFailed;
import csw.services.event.helpers.Utils;
import csw.services.event.internal.wiring.FailedEvent;
import csw.services.event.javadsl.IEventPublisher;
import net.manub.embeddedkafka.EmbeddedKafka$;
import org.junit.*;
import org.junit.rules.ExpectedException;
import scala.concurrent.Await;
import scala.concurrent.duration.FiniteDuration;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.isA;

public class JKafkaFailureTest {

    private static KafkaTestProps kafkaTestProps;
    private static IEventPublisher publisher;

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @BeforeClass
    public static void beforeClass() throws Exception {
        kafkaTestProps = KafkaTestProps.jCreateKafkaProperties(3564, 6001, Collections.singletonMap("message.max.bytes", "1"));
        publisher = kafkaTestProps.jPublisher();
        EmbeddedKafka$.MODULE$.start(kafkaTestProps.config());
    }

    @AfterClass
    public static void afterClass() throws Exception {
        kafkaTestProps.jPublisher().shutdown().get(10, TimeUnit.SECONDS);
        EmbeddedKafka$.MODULE$.stop();
        Await.result(kafkaTestProps.wiring().shutdown(CoordinatedShutdownReasons.TestFinishedReason$.MODULE$), new FiniteDuration(10, TimeUnit.SECONDS));
    }

    @Test
    public void failureInPublishingShouldFailFutureWithPublishFailedException() throws InterruptedException, ExecutionException, TimeoutException {
        exception.expectCause(isA(PublishFailed.class));

        // simulate publishing failure as message size is greater than message.max.bytes(1 byte) configured in broker
        publisher.publish(Utils.makeEvent(2)).get(10, TimeUnit.SECONDS);
    }

    @Test
    public void handleFailedPublishEventWithACallback() {

        TestProbe testProbe = TestProbe.create(Adapter.toTyped(kafkaTestProps.wiring().actorSystem()));
        Event event = Utils.makeEvent(1);
        Source eventStream = Source.single(event);

        publisher.publish(eventStream, (event1, ex) -> testProbe.ref().tell(new FailedEvent(event1, ex)));

        FailedEvent failedEvent = (FailedEvent) testProbe.expectMessageClass(FailedEvent.class);

        Assert.assertEquals(event, failedEvent.event());
        Assert.assertTrue(failedEvent.throwable() instanceof PublishFailed);
    }

    @Test
    public void handleFailedPublishEventWithAnEventGeneratorAndACallback() {
        TestProbe testProbe = TestProbe.create(Adapter.toTyped(kafkaTestProps.wiring().actorSystem()));
        Event event = Utils.makeEvent(1);

        publisher.publish(() -> event, new FiniteDuration(20, TimeUnit.MILLISECONDS), (event1, ex) -> testProbe.ref().tell(new FailedEvent(event1, ex)));

        FailedEvent failedEvent = (FailedEvent) testProbe.expectMessageClass(FailedEvent.class);

        Assert.assertEquals(event, failedEvent.event());
        Assert.assertTrue(failedEvent.throwable() instanceof PublishFailed);
    }
}