package ch.usi.dag.dislreserver.msg.stringinfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import ch.usi.dag.dislreserver.DiSLREServerException;
import ch.usi.dag.dislreserver.reqdispatch.RequestHandler;
import ch.usi.dag.dislreserver.shadow.ShadowObjectTable;


public final class StringInfoHandler implements RequestHandler {

    @Override
    public void handle (
        final DataInputStream is, final DataOutputStream os,
        final boolean debug
    ) throws DiSLREServerException {

        try {
            final long netReference = is.readLong ();
            final String value = is.readUTF ();

            ShadowObjectTable.registerShadowString (netReference, value);

        } catch (final IOException e) {
            throw new DiSLREServerException (e);
        }
    }


    @Override
    public void exit () {
        // do nothing
    }

}
