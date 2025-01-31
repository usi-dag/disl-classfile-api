package ch.usi.dag.disl.test.suite.classinfo.instr;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.marker.BodyMarker;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;

public class DiSLClass {

    @Before(marker = BodyMarker.class, scope = "TargetClass.print(boolean)", order = 0)
    public static void precondition(final ClassStaticContext csc) {
        System.out.println("disl: class " + csc.getName ());
    }

}
