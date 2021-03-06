package example.framework.components.hcd;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import csw.command.client.CommandResponseManager;
import csw.command.client.messages.TopLevelActorMessage;
import csw.command.client.models.framework.ComponentInfo;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.api.models.ConfigData;
import csw.event.api.javadsl.IEventService;
import csw.framework.CurrentStatePublisher;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.LocationRemoved;
import csw.location.api.models.LocationUpdated;
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.*;
import example.framework.components.ConfigNotAvailableException;
import example.framework.components.assembly.WorkerActor;
import example.framework.components.assembly.WorkerActorMsg;
import example.framework.components.assembly.WorkerActorMsgs;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

//#jcomponent-handlers-class
public class JHcdComponentHandlers extends JComponentHandlers {

    private final ActorContext<TopLevelActorMessage> ctx;
    private final ComponentInfo componentInfo;
    private final CommandResponseManager commandResponseManager;
    private final CurrentStatePublisher currentStatePublisher;
    private final ILocationService locationService;
    private final IEventService eventService;
    private ILogger log;
    private IConfigClientService configClient;
    private ConfigData hcdConfig;
    private ActorRef<WorkerActorMsg> worker;
    private int current;
    private int stats;

    public JHcdComponentHandlers(
            akka.actor.typed.javadsl.ActorContext<TopLevelActorMessage> ctx,
            JCswContext cswCtx
    ) {
        super(ctx, cswCtx);
        this.ctx = ctx;
        this.componentInfo = cswCtx.componentInfo();
        this.commandResponseManager = cswCtx.commandResponseManager();
        this.currentStatePublisher = cswCtx.currentStatePublisher();
        this.locationService = cswCtx.locationService();
        this.eventService = cswCtx.eventService();
        log = cswCtx.loggerFactory().getLogger(this.getClass());
    }
    //#jcomponent-handlers-class


    //#jInitialize-handler
    @Override
    public CompletableFuture<Void> jInitialize() {

        // fetch config (preferably from configuration service)
        getConfig().thenAccept(config -> hcdConfig = config);

        // create a worker actor which is used by this hcd
        worker = ctx.spawnAnonymous(WorkerActor.behavior(hcdConfig));

        // initialise some state by using the worker actor created above
        CompletionStage<Integer> askCurrent = AskPattern.ask(worker, WorkerActorMsgs.JInitialState::new, Duration.ofSeconds(5), ctx.getSystem().scheduler());
        CompletableFuture<Void> currentFuture = askCurrent.thenAccept(c -> current = c).toCompletableFuture();

        CompletionStage<Integer> askStats = AskPattern.ask(worker, WorkerActorMsgs.JInitialState::new, Duration.ofSeconds(5), ctx.getSystem().scheduler());
        CompletableFuture<Void> statsFuture = askStats.thenAccept(s -> stats = s).toCompletableFuture();

        return CompletableFuture.allOf(currentFuture, statsFuture);
    }

    //#jInitialize-handler
    //#validateCommand-handler
    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup) {
            // validation for setup goes here
            return new CommandResponse.Accepted(controlCommand.runId());
        } else if (controlCommand instanceof Observe) {
            // validation for observe goes here
            return new CommandResponse.Accepted(controlCommand.runId());
        } else {
            return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.UnsupportedCommandIssue(controlCommand.commandName().name()));
        }
    }
    //#validateCommand-handler

    //#onSubmit-handler
    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup)
            return submitSetup((Setup) controlCommand); // includes logic to handle Submit with Setup config command
        else if (controlCommand instanceof Observe)
            return submitObserve((Observe) controlCommand); // includes logic to handle Submit with Observe config command"
        else
            return new CommandResponse.Error(controlCommand.runId(), "Unknown command: " + controlCommand.commandName().name());
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

    private CommandResponse.SubmitResponse processSetup(Setup sc) {
        switch (sc.commandName().name()) {
            case "axisMove":
            case "axisDatum":
            case "axisHome":
            case "axisCancel":
            default:
                log.error("Invalid command [" + sc + "] received.");
        }
        return new CommandResponse.Completed(sc.runId());
    }

    private CommandResponse.SubmitResponse processObserve(Observe oc) {
        switch (oc.commandName().name()) {
            case "point":
            case "acquire":
            default:
                log.error("Invalid command [" + oc + "] received.");
        }
        return new CommandResponse.Completed(oc.runId());
    }

    /**
     * in case of submit command, component writer is required to update commandResponseManager with the result
     */
    private CommandResponse.SubmitResponse submitSetup(Setup setup) {
        return processSetup(setup);
    }

    private CommandResponse.SubmitResponse submitObserve(Observe observe) {
        return processObserve(observe);
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
