package csw.event.cli.args

import csw.event.cli.BuildInfo
import scopt.OptionParser

/**
 * Parses the command line options using `scopt` library.
 */
class ArgsParser(name: String) {

  val parser: OptionParser[Options] = new scopt.OptionParser[Options](name) with Arguments {
    head(name, BuildInfo.version)

    cmd("inspect")
      .action((_, c) => c.copy(cmd = "inspect"))
      .text("returns event information excluding parameter values")
      .children(eventkeys)

    cmd("get")
      .action((_, c) => c.copy(cmd = "get"))
      .text("returns event")
      .children(eventkeysWithPath, out, timestamp, id, units)

    cmd("publish")
      .action((_, c) => c.copy(cmd = "publish"))
      .text("publishes event provided from input file")
      .children(
        eventkey,
        data,
        params,
        interval.text("interval in [ms] to publish event, single event will be published if not provided"),
        period
      )

    cmd("subscribe")
      .action((_, c) => c.copy(cmd = "subscribe"))
      .text("returns event")
      .children(
        eventkeysWithPath,
        interval.text("interval in [ms]: receive an event exactly at each tick"),
        out,
        timestamp,
        id,
        units
      )

    opt[String]("locationHost") action { (x, c) =>
      c.copy(locationHost = x)
    } text "Optional: host address of machine where location server is running. Default is localhost"

    help("help")

    version("version")

    checkConfig { c =>
      if (c.cmd.isEmpty)
        failure("""
                  |Please specify one of the following command with their corresponding options:
                  |  1> inspect
                  |  2> get
                  |  3> subscribe
                  |  4> publish
                """.stripMargin)
      else success
    }
  }

  /**®
   * Parses the command line arguments and returns a value if they are valid.
   *
   * @param args the command line arguments
   * @return an object containing the parsed values of the command line arguments
   */
  def parse(args: Seq[String]): Option[Options] = parser.parse(args, Options())

}
