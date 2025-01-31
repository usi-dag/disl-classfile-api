package ch.usi.dag.disl.test.suite.sendspecial.app;

import ch.usi.dag.dislre.REDispatch;


public class TargetClass {

    private static short __stringEventId__ = REDispatch.registerMethod (
        "ch.usi.dag.disl.test.suite.sendspecial.instr.Analysis.stringEvent"
    );


    static void sendString (final boolean isSpecial, final String string) {
        REDispatch.analysisStart (__stringEventId__);
        __sendObject (isSpecial, string);
        REDispatch.analysisEnd ();
    }

    //

    private static short __threadEventId__ = REDispatch.registerMethod (
        "ch.usi.dag.disl.test.suite.sendspecial.instr.Analysis.threadEvent"
    );


    static void sendThread (final boolean isSpecial, final Thread thread) {
        REDispatch.analysisStart (__threadEventId__);
        __sendObject (isSpecial, thread);
        REDispatch.analysisEnd ();
    }

    //

    private static short __emptyEventId__ = REDispatch.registerMethod (
        "ch.usi.dag.disl.test.suite.sendspecial.instr.Analysis.emptyEvent"
    );


    static void sendEmpty () {
        REDispatch.analysisStart (__emptyEventId__);
        REDispatch.analysisEnd ();
    }

    //

    private static void __sendObject (final boolean isSpecial, final Object object) {
        REDispatch.sendBoolean (isSpecial);

        if (isSpecial) {
            REDispatch.sendObjectPlusData (object);
        } else {
            REDispatch.sendObject (object);
        }
    }

    //

    public static void main (final String [] args) throws InterruptedException {
        final String string = "Hello, World!";
        sendString (false, string);

        final Thread thread = new Thread ("Newly Thread");
        thread.setDaemon (true);
        sendThread (false, thread);

        // Send a lot of empty events to force the agent to flush buffers.
        for (int i = 0; i < 100000; i++) {
            sendEmpty ();
        }

        sendString (true, string);
        sendThread (true, thread);
    }

}
