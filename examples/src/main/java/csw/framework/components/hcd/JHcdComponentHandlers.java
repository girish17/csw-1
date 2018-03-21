package csw.framework.components.hcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;
import csw.framework.components.ConfigNotAvailableException;
import csw.framework.components.assembly.WorkerActor;
import csw.framework.components.assembly.WorkerActorMsg;
import csw.framework.components.assembly.WorkerActorMsgs;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.common.commands.CommandResponse;
import csw.common.commands.ControlCommand;
import csw.common.commands.Observe;
import csw.common.commands.Setup;
import csw.common.framework.ComponentInfo;
import csw.common.location.LocationRemoved;
import csw.common.location.LocationUpdated;
import csw.common.location.TrackingEvent;
import csw.common.scaladsl.TopLevelActorMessage;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

//#jcomponent-handlers-class
public class JHcdComponentHandlers extends JComponentHandlers {

    private final ActorContext<TopLevelActorMessage> ctx;
    private final ComponentInfo componentInfo;
    private final CommandResponseManager commandResponseManager;
    private final CurrentStatePublisher currentStatePublisher;
    private final ILocationService locationService;
    private ILogger log;
    private IConfigClientService configClient;
    private ConfigData hcdConfig;
    private ActorRef<WorkerActorMsg> worker;
    private int current;
    private int stats;

    public JHcdComponentHandlers(
            akka.actor.typed.javadsl.ActorContext<TopLevelActorMessage> ctx,
            ComponentInfo componentInfo,
            CommandResponseManager commandResponseManager,
            CurrentStatePublisher currentStatePublisher,
            ILocationService locationService,
            JLoggerFactory loggerFactory

    ) {
        super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory);
        this.ctx = ctx;
        this.componentInfo = componentInfo;
        this.commandResponseManager = commandResponseManager;
        this.currentStatePublisher = currentStatePublisher;
        this.locationService = locationService;
        log = loggerFactory.getLogger(this.getClass());
    }
    //#jcomponent-handlers-class


    //#jInitialize-handler
    @Override
    public CompletableFuture<Void> jInitialize() {

        // fetch config (preferably from configuration service)
        getConfig().thenAccept(config -> hcdConfig = config);

        // create a worker actor which is used by this hcd
        worker = ctx.spawnAnonymous(WorkerActor.make(hcdConfig));

        // initialise some state by using the worker actor created above
        CompletionStage<Integer> askCurrent = AskPattern.ask(worker, WorkerActorMsgs.JInitialState::new, new Timeout(5, TimeUnit.SECONDS), ctx.getSystem().scheduler());
        CompletableFuture<Void> currentFuture = askCurrent.thenAccept(c -> current = c).toCompletableFuture();

        CompletionStage<Integer> askStats = AskPattern.ask(worker, WorkerActorMsgs.JInitialState::new, new Timeout(5, TimeUnit.SECONDS), ctx.getSystem().scheduler());
        CompletableFuture<Void> statsFuture = askStats.thenAccept(s -> stats = s).toCompletableFuture();

        return CompletableFuture.allOf(currentFuture, statsFuture);
    }
    //#jInitialize-handler
    //#validateCommand-handler
    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup) {
            // validation for setup goes here
            return new CommandResponse.Accepted(controlCommand.runId());
        } else if (controlCommand instanceof Observe) {
            // validation for observe goes here
            return new CommandResponse.Accepted(controlCommand.runId());
        } else {
            return new CommandResponse.CommandNotAvailable(controlCommand.runId());
        }
    }
    //#validateCommand-handler

    //#onSubmit-handler
    @Override
    public void onSubmit(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup)
            submitSetup((Setup) controlCommand); // includes logic to handle Submit with Setup config command
        else if (controlCommand instanceof Observe)
            submitObserve((Observe) controlCommand); // includes logic to handle Submit with Observe config command
    }
    //#onSubmit-handler

    //#onOneway-handler
    @Override
    public void onOneway(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup)
            onewaySetup((Setup) controlCommand); // includes logic to handle Oneway with Setup config command
        else if (controlCommand instanceof Observe)
            onewayObserve((Observe) controlCommand); // includes logic to handle Oneway with Observe config command
    }
    //#onOneway-handler

    //#onGoOffline-handler
    @Override
    public void onGoOffline() {
        // do something when going offline
    }
    //#onGoOffline-handler

    //#onGoOnline-handler
    @Override
    public void onGoOnline() {
        // do something when going online
    }
    //#onGoOnline-handler

    //#onShutdown-handler
    @Override
    public CompletableFuture<Void> jOnShutdown() {
        return CompletableFuture.runAsync(() -> {
            // clean up resources
        });
    }
    //#onShutdown-handler

    //#onLocationTrackingEvent-handler
    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
        if (trackingEvent instanceof LocationUpdated) {
            // do something for the tracked location when it is updated
        } else if (trackingEvent instanceof LocationRemoved) {
            // do something for the tracked location when it is no longer available
        }
    }
    //#onLocationTrackingEvent-handler

    private void processSetup(Setup sc) {
        switch (sc.commandName().name()) {
            case "axisMove":
            case "axisDatum":
            case "axisHome":
            case "axisCancel":
            default:
                log.error("Invalid command [" + sc + "] received.");
        }
    }

    private void processObserve(Observe oc) {
        switch (oc.commandName().name()) {
            case "point":
            case "acquire":
            default:
                log.error("Invalid command [" + oc + "] received.");
        }
    }

    /**
     * in case of submit command, component writer is required to update commandResponseManager with the result
     */
    private void submitSetup(Setup setup) {
        processSetup(setup);
    }

    private void submitObserve(Observe observe) {
        processObserve(observe);
    }

    private void onewaySetup(Setup setup) {
        processSetup(setup);
    }

    private void onewayObserve(Observe observe) {
        processObserve(observe);
    }

    private CompletableFuture<ConfigData> getConfig() {
        // required configuration could not be found in the configuration service. Component can choose to stop until the configuration is made available in the
        // configuration service and started again
        return configClient.getActive(Paths.get("tromboneAssemblyContext.conf")).thenApply((maybeConfigData) -> maybeConfigData.<ConfigNotAvailableException>orElseThrow(ConfigNotAvailableException::new));
    }
}
