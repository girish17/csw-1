package csw.framework.components.assembly;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.testkit.typed.javadsl.TestProbe;
import akka.util.Timeout;
import csw.framework.exceptions.FailureRestart;
import csw.framework.exceptions.FailureStop;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.common.commands.*;
import csw.common.framework.ComponentInfo;
import csw.common.location.*;
import csw.common.params.generics.JKeyTypes;
import csw.common.params.generics.Key;
import csw.common.params.models.Prefix;
import csw.common.params.states.CurrentState;
import csw.common.scaladsl.TopLevelActorMessage;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.config.api.javadsl.IConfigClientService;
import csw.services.config.api.models.ConfigData;
import csw.services.config.client.javadsl.JConfigClientFactory;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JComponentType;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.concurrent.duration.FiniteDuration;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


//#jcomponent-handlers-class
public class JAssemblyComponentHandlers extends JComponentHandlers {

    private final ActorContext<TopLevelActorMessage> ctx;
    private final ComponentInfo componentInfo;
    private final CommandResponseManager commandResponseManager;
    private final CurrentStatePublisher currentStatePublisher;
    private final ILocationService locationService;
    private ILogger log;
    private IConfigClientService configClient;
    private Map<Connection, Optional<JCommandService>> runningHcds;
    private ActorRef<DiagnosticPublisherMessages> diagnosticPublisher;
    private ActorRef<CommandResponse> commandResponseAdapter;

    public JAssemblyComponentHandlers(
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
        configClient = JConfigClientFactory.clientApi(Adapter.toUntyped(ctx.getSystem()), locationService);

        runningHcds = new HashMap<>();
        commandResponseAdapter = TestProbe.<CommandResponse>create(ctx.getSystem()).ref();
    }
    //#jcomponent-handlers-class

    //#jInitialize-handler
    @Override
    public CompletableFuture<Void> jInitialize() {

        // fetch config (preferably from configuration service)
        CompletableFuture<ConfigData> configDataCompletableFuture = getAssemblyConfig();

        // create a worker actor which is used by this assembly
        CompletableFuture<ActorRef<WorkerActorMsg>> worker =
                configDataCompletableFuture.thenApply(config -> ctx.spawnAnonymous(WorkerActor.make(config)));

        // find a Hcd connection from the connections provided in componentInfo
        Optional<Connection> mayBeConnection = componentInfo.getConnections().stream()
                .filter(connection -> connection.componentId().componentType() == JComponentType.HCD)
                .findFirst();

        // If an Hcd is found as a connection, resolve its location from location service and create other
        // required worker actors required by this assembly
        return mayBeConnection.map(connection ->
                worker.thenAcceptBoth(resolveHcd(), (workerActor, hcdLocation) -> {
                    if(!hcdLocation.isPresent())
                        throw new HcdNotFoundException();
                    else
                        runningHcds.put(connection, Optional.of(new JCommandService(hcdLocation.get(), ctx.getSystem())));
                    diagnosticPublisher = ctx.spawnAnonymous(JDiagnosticsPublisherFactory.make(new JCommandService(hcdLocation.get(), ctx.getSystem()), workerActor));
                })).get();

    }
    //#jInitialize-handler

    //#validateCommand-handler
    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
        if (controlCommand instanceof Setup) {
            // validation for setup goes here
            //#addOrUpdateCommand
            // after validation of the controlCommand, update its status of successful validation as Accepted
            CommandResponse.Accepted accepted = new CommandResponse.Accepted(controlCommand.runId());
            commandResponseManager.addOrUpdateCommand(controlCommand.runId(), accepted);
            //#addOrUpdateCommand
            return new CommandResponse.Accepted(controlCommand.runId());
        } else if (controlCommand instanceof Observe) {
            // validation for observe goes here
            return new CommandResponse.Accepted(controlCommand.runId());
        } else {
            return new CommandResponse.Invalid(controlCommand.runId(), new CommandIssue.AssemblyBusyIssue("Command not supported"));
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
        // clean up resources
        return new CompletableFuture<>();
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
            case "forwardToWorker":
                //#addSubCommand
                Prefix prefix = new Prefix("wfos.red.detector");
                Setup subCommand = new Setup(prefix, new CommandName("sub-command-1"), sc.jMaybeObsId());
                commandResponseManager.addSubCommand(sc.runId(), subCommand.runId());

                Setup subCommand2 = new Setup(prefix, new CommandName("sub-command-2"), sc.jMaybeObsId());
                commandResponseManager.addSubCommand(sc.runId(), subCommand2.runId());

                //#addSubCommand

                //#subscribe-to-command-response-manager
                // subscribe to the status of original command received and publish the state when its status changes to
                // Completed
                commandResponseManager.jSubscribe(subCommand.runId(), commandResponse -> {
                    if(commandResponse.resultType() instanceof CommandResponse.Completed) {
                        Key<String> stringKey = JKeyTypes.StringKey().make("sub-command-status");
                        CurrentState currentState = new CurrentState(sc.source().prefix());
                        currentStatePublisher.publish(currentState.madd(stringKey.set("complete")));
                    }
                    else {
                        // do something
                    }
                });
                //#subscribe-to-command-response-manager

                //#updateSubCommand
                // An original command is split into sub-commands and sent to a component. The result of the command is
                // obtained by subscribing to the component with the sub command id.
                JCommandService componentCommandService = runningHcds.get(componentInfo.getConnections().get(0)).get();
                componentCommandService.subscribe(subCommand2.runId(), Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS)))
                        .thenAccept(commandResponse -> {
                            if(commandResponse.resultType() instanceof CommandResponse.Completed) {
                                // As the commands get completed, the results are updated in the commandResponseManager
                                commandResponseManager.updateSubCommand(subCommand2.runId(), commandResponse);
                            }
                            else {
                                // do something
                            }
                });
                //#updateSubCommand

                //#query-command-response-manager
                // query CommandResponseManager to get the current status of Command, for example: Accepted/Completed/Invalid etc.
                commandResponseManager
                        .jQuery(subCommand.runId(), Timeout.durationToTimeout(FiniteDuration.apply(5, "seconds")))
                        .thenAccept(commandResponse -> {
                            // may choose to publish current state to subscribers or do other operations
                        });

                //#query-command-response-manager

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

    /**
     * Below methods are just here to show how exceptions can be used to either restart or stop supervisor
     * This are snipped in paradox documentation
     * */

    // #failureRestart-Exception
    class HcdNotFoundException extends FailureRestart {
        HcdNotFoundException() {
            super("Could not resolve hcd location. Initialization failure.");
        }
    }

    private CompletableFuture<Optional<AkkaLocation>> resolveHcd() {
        // find a Hcd connection from the connections provided in componentInfo
        Optional<Connection> mayBeConnection = componentInfo.getConnections().stream()
                .filter(connection -> connection.componentId().componentType() == JComponentType.HCD)
                .findFirst();

        // If an Hcd is found as a connection, resolve its location from location service and create other
        // required worker actors required by this assembly
        if(mayBeConnection.isPresent()) {
            CompletableFuture<Optional<AkkaLocation>> resolve = locationService.resolve(mayBeConnection.get().<AkkaLocation>of(), FiniteDuration.apply(5, TimeUnit.SECONDS));
            return resolve.thenCompose((Optional<AkkaLocation> resolvedHcd) -> {
                if(resolvedHcd.isPresent())
                    return CompletableFuture.completedFuture(resolvedHcd);
                else
                    throw new ConfigNotAvailableException();
            });
        }
        else
            return CompletableFuture.completedFuture(Optional.empty());
    }
    // #failureRestart-Exception

    // #failureStop-Exception
    class ConfigNotAvailableException extends FailureStop {
        ConfigNotAvailableException() {
            super("Configuration not available. Initialization failure.");
        }
    }

    private CompletableFuture<ConfigData> getAssemblyConfig() throws ConfigNotAvailableException {
        // required configuration could not be found in the configuration service. Component can choose to stop until the configuration is made available in the
        // configuration service and started again
        return configClient.getActive(Paths.get("tromboneAssemblyContext.conf"))
                .thenApply((Optional<ConfigData> maybeConfigData) -> {
                    return maybeConfigData.<ConfigNotAvailableException>orElseThrow(() -> new ConfigNotAvailableException());
                });
    }
    // #failureStop-Exception

}
