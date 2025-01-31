package ch.usi.dag.disl.test.suite.bypassmodulevisibility.app;

import java.util.logging.Logger;

public class TargetClass {

    public static void main(String[] args) {
        //
        // try to instrument a class in another module
        // BypassCheck should be visible to it
        //
        final Logger LOGGER = Logger.getGlobal();
    }

}
