package ch.usi.dag.dislreserver.msg.threadinfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import ch.usi.dag.dislreserver.DiSLREServerException;
import ch.usi.dag.dislreserver.reqdispatch.RequestHandler;
import ch.usi.dag.dislreserver.shadow.ShadowObjectTable;


public class ThreadInfoHandler implements RequestHandler {

    @Override
    public void handle (
        final DataInputStream is, final DataOutputStream os,
        final boolean debug
    ) throws DiSLREServerException {

        try {
            final long netReference = is.readLong ();
            final String name = is.readUTF ();
            final boolean isDaemon = is.readBoolean ();

            ShadowObjectTable.registerShadowThread (
                netReference, name, isDaemon
            );

        } catch (final IOException e) {
            throw new DiSLREServerException (e);
        }
    }


    @Override
    public void exit () {
        // do nothing
    }

}
