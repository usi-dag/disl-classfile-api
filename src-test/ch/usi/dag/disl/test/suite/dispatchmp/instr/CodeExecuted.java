package ch.usi.dag.disl.test.suite.dispatchmp.instr;

import java.util.concurrent.atomic.AtomicLong;

import ch.usi.dag.dislreserver.remoteanalysis.RemoteAnalysis;
import ch.usi.dag.dislreserver.shadow.ShadowObject;


public final class CodeExecuted extends RemoteAnalysis {

    AtomicLong totalIntEvents = new AtomicLong();
    AtomicLong totalObjEvents = new AtomicLong();
    AtomicLong totalFreeEvents = new AtomicLong();

    public void intEvent(final int number) {
        final long count = totalIntEvents.incrementAndGet();
        if ((count % 1_000_000) == 0) {
            System.out.println("So far received "+ count +" events...");
        }
    }

    public void objectEvent(final ShadowObject o) {
        totalObjEvents.incrementAndGet();
    }

    @Override
    public void objectFree(final ShadowObject netRef) {
        totalFreeEvents.incrementAndGet();
    }

    @Override
    public void atExit() {
        System.out.println("Total number of int events: "+ totalIntEvents);
        System.out.println("Total number of object events: "+ totalObjEvents);
        System.out.println("Total number of free events: "+ totalFreeEvents);
    }
}
