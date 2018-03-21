import sbt._

object Dependencies {

  val Common = Seq(
    Libs.`scala-java8-compat`,
    Enumeratum.`enumeratum`,
    Libs.`play-json`,
    Libs.`play-json-extensions`,
    Enumeratum.`enumeratum-play`,
    Chill.`chill-bijection`,
    Libs.`scalapb-runtime`,
    Libs.`scalapb-json4s`,
    Akka.`akka-actor-typed`,
    Akka.`akka-stream`,
    Akka.`akka-cluster-tools` % Test,
    Akka.`akka-testkit-typed` % Test,
    Akka.`akka-actor`         % Test,
    Chill.`chill-akka`        % Test,
    Libs.`scalatest`          % Test,
    Libs.`junit`              % Test,
    Libs.`junit-interface`    % Test
  )

  val Logging = Seq(
    Libs.`logback-classic`,
    Libs.`persist-json`,
    Libs.`joda-time`,
    Enumeratum.`enumeratum`,
    Akka.`akka-actor`,
    Akka.`akka-slf4j`,
    Akka.`akka-remote`,
    Akka.`akka-actor-typed`,
    Chill.`chill-akka`     % Test,
    Libs.`scalatest`       % Test,
    Libs.`junit`           % Test,
    Libs.`junit-interface` % Test,
    Libs.`gson`            % Test
  )

  val Benchmark = Seq(
    Libs.`persist-json`,
    Libs.`gson`,
    Jackson.`jackson-core`,
    Jackson.`jackson-databind`,
    Chill.`chill-akka`
  )

  val Location = Seq(
    Akka.`akka-actor-typed`,
    Akka.`akka-testkit-typed`,
    Akka.`akka-stream`,
    Akka.`akka-distributed-data`,
    Akka.`akka-remote`,
    Akka.`akka-cluster-tools`,
    Akka.`akka-cluster-typed`,
    Libs.`scala-java8-compat`,
    Libs.`scala-async`,
    Enumeratum.`enumeratum`,
    Libs.`akka-management-cluster-http`,
    Chill.`chill-akka`,
    AkkaHttp.`akka-http`,
    Libs.`scalatest`               % Test,
    Libs.`junit`                   % Test,
    Libs.`junit-interface`         % Test,
    Libs.`mockito-core`            % Test,
    Akka.`akka-stream-testkit`     % Test,
    Akka.`akka-multi-node-testkit` % Test
  )

  val LocationAgent = Seq(
    Akka.`akka-actor`,
    Libs.`scopt`,
    Libs.`scalatest` % Test
  )

  val ConfigClientCli = Seq(
    Akka.`akka-actor`,
    Libs.`scopt`,
    Libs.`scalatest`               % Test,
    Akka.`akka-multi-node-testkit` % Test
  )

  val Integration = Seq(
    Libs.`scalatest`,
    Akka.`akka-stream-testkit`
  )

  val ConfigApi = Seq(
    Enumeratum.`enumeratum`,
    Akka.`akka-stream`,
    Libs.`akka-http-play-json`,
    Libs.`play-json`,
    Libs.`scalatest`           % Test,
    Akka.`akka-stream-testkit` % Test
  )

  val ConfigServer = Seq(
    AkkaHttp.`akka-http`,
    Libs.svnkit,
    Libs.`scopt`,
    Libs.`scalatest`             % Test,
    AkkaHttp.`akka-http-testkit` % Test,
    Akka.`akka-stream-testkit`   % Test
  )

  val ConfigClient = Seq(
    AkkaHttp.`akka-http`,
    Libs.`scalatest`               % Test,
    Libs.`junit`                   % Test,
    Libs.`junit-interface`         % Test,
    Libs.`mockito-core`            % Test,
    Akka.`akka-multi-node-testkit` % Test,
    Akka.`akka-stream-testkit`     % Test
  )

  val ClusterSeed = Seq(
    AkkaHttp.`akka-http`,
    Libs.`play-json`,
    Libs.`akka-http-play-json`,
    Libs.`scopt`,
    Libs.`scalatest` % Test
  )

  val Framework = Seq(
    Libs.`scala-async`,
    Libs.`play-json`,
    Enumeratum.`enumeratum-play`,
    Akka.`akka-actor-typed`,
    Libs.`scopt`,
    Akka.`akka-testkit-typed`  % Test,
    Akka.`akka-stream-testkit` % Test,
    Libs.`scalatest`           % Test,
    Libs.`junit`               % Test,
    Libs.`junit-interface`     % Test,
    Libs.`mockito-core`        % Test
  )

  val Command = Seq(
    Libs.`scala-async`,
    Akka.`akka-actor-typed`,
    Akka.`akka-testkit-typed`  % Test,
    Akka.`akka-stream-testkit` % Test,
    Libs.`scalatest`           % Test,
    Chill.`chill-akka`         % Test,
    Libs.`junit`               % Test,
    Libs.`junit-interface`     % Test,
    Libs.`mockito-core`        % Test
  )

  val EventImpl = Seq(
    Libs.`scala-async`,
    Akka.`akka-stream`,
    Libs.`akka-stream-kafka`,
    Akka.`akka-actor-typed`,
    Libs.`lettuce`,
    Akka.`akka-testkit-typed`       % Test,
    Akka.`akka-stream-testkit`      % Test,
    Libs.`scalatest`                % Test,
    Libs.`junit`                    % Test,
    Libs.`junit-interface`          % Test,
    Libs.`mockito-core`             % Test,
    Libs.`embedded-redis`           % Test,
    Libs.`scalatest-embedded-kafka` % Test
  )

  val Commons = Seq(
    Akka.`akka-stream`,
    AkkaHttp.`akka-http`,
    Libs.`play-json`,
    Libs.`scalatest` % Test
  )

  val Deploy = Seq(
    Libs.`scalatest` % Test
  )

  val Examples = Seq(
    AkkaHttp.`akka-http`,
    Libs.`scalatest`       % Test,
    Libs.`junit`           % Test,
    Libs.`junit-interface` % Test
  )

  val SequencerPrototype = Seq(
    Libs.`scalatest` % Test,
    Akka.`akka-stream`,
    Akka.`akka-actor-typed`,
    Akka.`akka-testkit-typed`,
    Libs.`scala-reflect`,
    Libs.`scala-compiler`,
    Ammonite.`ammonite`,
    Ammonite.`ammonite-sshd`
  )

  val AcceptanceTests = Seq(
    Libs.`scalatest`
  )
}
