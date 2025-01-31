package ch.usi.dag.disl.test.suite.sendspecial.instr;

import ch.usi.dag.dislreserver.remoteanalysis.RemoteAnalysis;
import ch.usi.dag.dislreserver.shadow.ShadowObject;
import ch.usi.dag.dislreserver.shadow.ShadowString;
import ch.usi.dag.dislreserver.shadow.ShadowThread;


public class Analysis extends RemoteAnalysis {

    public void threadEvent (final boolean isSpecial, final ShadowThread thread) {
        System.out.printf ("thread, is special: %s, name: %s, daemon: %s\n", isSpecial, thread.getName (), thread.isDaemon ());
    }


    public void stringEvent (final boolean isSpecial, final ShadowString string) {
        System.out.printf ("string, is special: %s, value: %s\n", isSpecial, string.toString ());
    }


    public void emptyEvent () {
        // Called to insert extra events into the client's buffer.
    }

    //

    @Override
    public void atExit () {
        // do nothing
    }


    @Override
    public void objectFree (final ShadowObject object) {
        // do nothing
    }

}
