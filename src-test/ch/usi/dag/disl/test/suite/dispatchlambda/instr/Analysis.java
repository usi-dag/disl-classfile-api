package ch.usi.dag.disl.test.suite.dispatchlambda.instr;

import java.util.concurrent.atomic.AtomicLong;

import ch.usi.dag.dislreserver.remoteanalysis.RemoteAnalysis;
import ch.usi.dag.dislreserver.shadow.ShadowObject;


public class Analysis extends RemoteAnalysis {

    private final AtomicLong __lambdaEventsTotal = new AtomicLong ();

    //

    public void lambdaEvent (final int index, final ShadowObject object) {
        final String name = object.getShadowClass ().getName ();
        
        //
        // In Java 1, slash has been replaced by dot
        //
        int lastIndex = name.lastIndexOf ("/");
        if (lastIndex == -1) {
            lastIndex = name.lastIndexOf (".");
        }

        System.out.println (index +": "+ name.substring (0, lastIndex));
        __lambdaEventsTotal.incrementAndGet ();
    }


    @Override
    public void atExit () {
        System.out.println ("Total number of lambda events: " + __lambdaEventsTotal);
    }


    @Override
    public void objectFree (final ShadowObject netRef) {
        // do nothing
    }

}
