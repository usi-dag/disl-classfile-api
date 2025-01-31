package ch.usi.dag.disl.test.suite.dispatchlambda.app;

import java.util.Comparator;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import ch.usi.dag.dislre.REDispatch;

public class TargetClass {

    private static short __lambdaEventId__ = REDispatch.registerMethod (
        "ch.usi.dag.disl.test.suite.dispatchlambda.instr.Analysis.lambdaEvent"
    );


    static void sendLambda (final int index, final Object object) {
        REDispatch.analysisStart (__lambdaEventId__);
        REDispatch.sendInt (index);
        REDispatch.sendObject (object);
        REDispatch.analysisEnd ();
    }

    //

    public static void main (final String [] args) throws InterruptedException {
        IntStream.range (0,  10).forEach (i ->
            sendLambda (i, (Comparator <String>) String::compareTo)
        );

        IntStream.range (10,  20).forEach (i -> {
            sendLambda (i, (IntFunction <String>) Integer::toString);
        });
    }

}
