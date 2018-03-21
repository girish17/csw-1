package csw.services.location;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.KillSwitch;
import akka.stream.Materializer;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import csw.common.commons.CoordinatedShutdownReasons;
import csw.common.location.*;
import csw.common.location.Connection.AkkaConnection;
import csw.common.location.Connection.HttpConnection;
import csw.common.scaladsl.ComponentMessage;
import csw.common.scaladsl.ContainerMessage;
import csw.services.location.commons.ActorSystemFactory;
import csw.services.location.javadsl.*;
import csw.services.location.models.AkkaRegistration;
import csw.services.location.models.HttpRegistration;
import csw.services.location.scaladsl.RegistrationFactory;
import csw.services.logging.commons.LogAdminActorFactory;
import csw.services.logging.messages.LogControlMessages;
import csw.services.logging.internal.LoggingSystem;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JKeys;
import csw.services.logging.javadsl.JLoggerFactory;
import csw.services.logging.javadsl.JLoggingSystemFactory;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * An example location service client application.
 */
//#actor-mixin
public class JLocationServiceExampleClient extends AbstractActor {

    private ILogger log = new JLoggerFactory("my-component-name").getLogger(context(), getClass());
    //#actor-mixin
    //#create-location-service
    private ILocationService locationService = JLocationServiceFactory.make();
    //#create-location-service

    private AkkaConnection exampleConnection = LocationServiceExampleComponent.connection();

    private IRegistrationResult httpRegResult;
    private IRegistrationResult hcdRegResult;
    private IRegistrationResult assemblyRegResult;

    private static LoggingSystem loggingSystem;


    public JLocationServiceExampleClient() throws ExecutionException, InterruptedException {

        // EXAMPLE DEMO START

        // This demo shows some basics of using the location service.  Before running this example,
        // optionally use the location service csw-location-agent to start a redis server:
        //
        //   $ csw-location-agent --name redis --command "redis-server --port %port"
        //
        // Not only does this serve as an example for starting applications that are not written in CSW,
        // but it also will help demonstrate location filtering later in this demo.


        registerConnectionsBlocking();
        findAndResolveConnectionsBlocking();
        listingAndFilteringBlocking();
        trackingAndSubscribingBlocking();
    }

    private class AllDone {
    }

    private void registerConnectionsBlocking() throws ExecutionException, InterruptedException {
        //#Components-Connections-Registrations

        // logAdminActorRef handles dynamically setting/getting log level of the component
        akka.actor.typed.ActorRef<LogControlMessages> logAdminActorRef = LogAdminActorFactory.make(context().system());

        // dummy http connection
        HttpConnection httpConnection = new HttpConnection(new ComponentId("configuration", JComponentType.Service));
        HttpRegistration httpRegistration = new HttpRegistration(httpConnection, 8080, "path123", logAdminActorRef);
        httpRegResult = locationService.register(httpRegistration).get();

        // ************************************************************************************************************

        // dummy HCD connection
        AkkaConnection hcdConnection = new AkkaConnection(new ComponentId("hcd1", JComponentType.HCD));
        ActorRef actorRef = getContext().actorOf(Props.create(AbstractActor.class, () -> new AbstractActor() {
                    @Override
                    public Receive createReceive() {
                        return ReceiveBuilder.create().build();
                    }
                }),
                "my-actor-1"
        );

        RegistrationFactory registrationFactory = new RegistrationFactory(logAdminActorRef);

        // Register UnTyped ActorRef with Location service. Use javadsl Adapter to convert UnTyped ActorRefs
        // to Typed ActorRef[Nothing]
        AkkaRegistration hcdRegistration = registrationFactory.akkaTyped(hcdConnection, "nfiraos.ncc.tromboneHcd", Adapter.toTyped(actorRef));
        hcdRegResult = locationService.register(hcdRegistration).get();

        // ************************************************************************************************************

        Behavior<String> behavior = Behaviors.setup(ctx -> {
            return Behaviors.same();
        });
        akka.actor.typed.ActorRef<String> typedActorRef = Adapter.spawn(context(), behavior, "typed-actor-ref");

        AkkaConnection assemblyConnection = new AkkaConnection(new ComponentId("assembly1", JComponentType.Assembly));

        // Register Typed ActorRef[String] with Location Service
        AkkaRegistration assemblyRegistration = registrationFactory.akkaTyped(assemblyConnection, "nfiraos.ncc.tromboneAssembly", typedActorRef);
        assemblyRegResult = locationService.register(assemblyRegistration).get();
        //#Components-Connections-Registrations
    }

    private void findAndResolveConnectionsBlocking() throws ExecutionException, InterruptedException {

        //#find
        // find connection to LocationServiceExampleComponent in location service
        // [do this before starting LocationServiceExampleComponent.  this should return Future[None]]

        //#log-info-map
        log.info("Attempting to find " + exampleConnection,
                new HashMap<String, Object>() {{
                    put(JKeys.OBS_ID, "foo_obs_id");
                    put("exampleConnection", exampleConnection.name());
                }});
        //#log-info-map

        Optional<AkkaLocation> findResult = locationService.find(exampleConnection).get();
        if (findResult.isPresent()) {
            //#log-info
            log.info("Find result: " + connectionInfo(findResult.get().connection()));
            //#log-info
        } else {
            log.info(() -> "Result of the find call : None");
        }
        //#find

        findResult.ifPresent(akkaLocation -> {
            //#typed-ref
            // If the component type is HCD or Assembly, use this to get the correct ActorRef
            akka.actor.typed.ActorRef<ComponentMessage> typedComponentRef = akkaLocation.componentRef();

            // If the component type is Container, use this to get the correct ActorRef
            akka.actor.typed.ActorRef<ContainerMessage> typedContainerRef = akkaLocation.containerRef();
            //#typed-ref
        });
        //#resolve
        // resolve connection to LocationServiceExampleComponent
        // [start LocationServiceExampleComponent after this command but before timeout]
        FiniteDuration waitForResolveLimit = new FiniteDuration(30, TimeUnit.SECONDS);

        //#log-info-map-supplier
        log.info(() -> "Attempting to resolve " + exampleConnection + " with a wait of " + waitForResolveLimit + "...", () -> {
            Map<String, Object> map = new HashMap<>();
            map.put(JKeys.OBS_ID, "foo_obs_id");
            map.put("exampleConnection", exampleConnection.name());
            return map;
        });
        //#log-info-map-supplier

        Optional<AkkaLocation> resolveResult = locationService.resolve(exampleConnection, waitForResolveLimit).get();
        if (resolveResult.isPresent()) {
            //#log-info-supplier
            log.info(() -> "Resolve result: " + connectionInfo(resolveResult.get().connection()));
            //#log-info-supplier
        } else {
            log.info(() -> "Timeout waiting for location " + exampleConnection + " to resolve.");
        }
        //#resolve

        // example code showing how to get the actorRef for remote component and send it a message
        if (resolveResult.isPresent()) {
            AkkaLocation loc = resolveResult.get();
            ActorRef actorRef = Adapter.toUntyped(loc.actorRef());
            actorRef.tell(LocationServiceExampleComponent.ClientMessage$.MODULE$, getSelf());
        }
    }

    private void listingAndFilteringBlocking() throws ExecutionException, InterruptedException {
        //#list
        // list connections in location service
        List<Location> connectionList = locationService.list().get();
        log.info("All Registered Connections:");
        for (Location loc : connectionList) {
            log.info("--- " + connectionInfo(loc.connection()));
        }
        //#list

        //#filtering-component
        // filter connections based on component type
        List<Location> componentList = locationService.list(JComponentType.Assembly).get();
        log.info("Registered Assemblies:");
        for (Location loc : componentList) {
            log.info("--- " + connectionInfo(loc.connection()));
        }
        //#filtering-component


        //#filtering-connection
        // filter connections based on connection type
        List<Location> akkaList = locationService.list(JConnectionType.AkkaType).get();
        log.info("Registered Akka connections:");
        for (Location loc : akkaList) {
            log.info("--- " + connectionInfo(loc.connection()));
        }
        //#filtering-connection

        //#filtering-prefix
        List<AkkaLocation> akkaLocations = locationService.listByPrefix("nfiraos.ncc").get();
        log.info("Registered akka locations for nfiraos.ncc");
        for (Location loc : akkaLocations) {
            log.info("--- " + connectionInfo(loc.connection()));
        }
        //#filtering-prefix
    }

    private void trackingAndSubscribingBlocking() {
        //#tracking
        // the following two methods are examples of two ways to track a connection.
        // both are implemented but only one is really needed.

        // track connection to LocationServiceExampleComponent
        // Calls track method for example connection and forwards location messages to this actor
        Materializer mat = ActorMaterializer.create(getContext());
        log.info("Starting to track " + exampleConnection);
        locationService.track(exampleConnection).toMat(Sink.actorRef(getSelf(), AllDone.class), Keep.both()).run(mat);

        //track returns a Killswitch, that can be used to turn off notifications arbitarily
        //in this case track a connection for 5 seconds, after that schedule switching off the stream
        Pair pair = locationService.track(exampleConnection).toMat(Sink.ignore(), Keep.both()).run(mat);
        context().system().scheduler().scheduleOnce(
                Duration.create(5, TimeUnit.SECONDS),
                () -> ((KillSwitch) pair.first()).shutdown(),
                context().system().dispatcher());

        // subscribe to LocationServiceExampleComponent events
        log.info("Starting a subscription to " + exampleConnection);
        locationService.subscribe(exampleConnection, trackingEvent -> {
            log.info("subscription event");
            getSelf().tell(trackingEvent, ActorRef.noSender());
        });
        //#tracking

        // [tracking shows component unregister and re-register]

    }

    private String connectionInfo(Connection connection) {
        // construct string with useful information about a connection
        return connection.name() + ", component type=" + connection.componentId().componentType() + ", connection type=" + connection.connectionType();
    }

    @Override
    public void postStop() throws ExecutionException, InterruptedException {
        //#unregister
        httpRegResult.unregister();
        hcdRegResult.unregister();
        assemblyRegResult.unregister();
        //#unregister

        try {
            //#shutdown
            locationService.shutdown(CoordinatedShutdownReasons.actorTerminatedReason()).get();
            //#shutdown
            // #log-info-error
        } catch (InterruptedException | ExecutionException ex) {
            log.info(ex.getMessage(), ex);
            throw ex;
        }
        //#log-info-error

        try {
            //#stop-logging-system
            // Only call this once per application
            loggingSystem.javaStop().get();
            //#stop-logging-system

        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
            throw ex;
        }
    }

    @Override
    public Receive createReceive() {
        // message handler for this actor
        return receiveBuilder()
                .match(LocationUpdated.class, l -> log.info("Location updated " + connectionInfo(l.connection())))
                .match(LocationRemoved.class, l -> log.info("Location removed " + l.connection()))
                .match(AllDone.class, x -> log.info("Tracking of " + exampleConnection + " complete."))
                .matchAny((Object x) -> {
                    //#log-info-error-supplier
                    RuntimeException runtimeException = new RuntimeException("Received unexpected message " + x);
                    log.info(() -> runtimeException.getMessage(), runtimeException);
                    //#log-info-error-supplier
                })
                .build();
    }

    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        //#create-actor-system
        ActorSystem actorSystem = ActorSystemFactory.remote("csw-examples-locationServiceClient");
        //#create-actor-system

        //#create-logging-system
        String host = InetAddress.getLocalHost().getHostName();
        loggingSystem = JLoggingSystemFactory.start("JLocationServiceExampleClient", "0.1", host, actorSystem);
        //#create-logging-system

        actorSystem.actorOf(Props.create(JLocationServiceExampleClient.class), "LocationServiceExampleClient");
    }
}
