package csw.trombone;

import csw.framework.javadsl.JContainerCmd;

import java.util.Optional;

//#container-app
public class JTromboneContainerCmdApp {

    public static void main(String args[]) {
        JContainerCmd.start("JTrombone-Container-Cmd-App", args, Optional.empty());
    }
}
//#container-app
