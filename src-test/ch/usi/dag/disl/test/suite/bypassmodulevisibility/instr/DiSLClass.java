package ch.usi.dag.disl.test.suite.bypassmodulevisibility.instr;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.annotation.After;
import ch.usi.dag.disl.marker.BodyMarker;

public class DiSLClass {

    @Before(marker = BodyMarker.class, order = 1, scope = "java.util.logging.Logger.getGlobal(..)")
    public static void beforeEvent() {
        Profiler.before();
    }

    @After(marker = BodyMarker.class, order = 1, scope = "java.util.logging.Logger.getGlobal(..)")
    public static void afterEvent() {
        Profiler.after();
    }

}
