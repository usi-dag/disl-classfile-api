package ch.usi.dag.dislreserver.msg.newclass;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import ch.usi.dag.dislreserver.DiSLREServerException;
import ch.usi.dag.dislreserver.reqdispatch.RequestHandler;
import ch.usi.dag.dislreserver.shadow.ShadowClassTable;


public class NewClassHandler implements RequestHandler {

    @Override
    public void handle (
        final DataInputStream is, final DataOutputStream os, final boolean debug
    ) throws DiSLREServerException {

        try {
            final String classInternalName = is.readUTF ();
            final long classLoaderNetReference = is.readLong ();
            final int classCodeLength = is.readInt ();
            final byte [] classCode = new byte [classCodeLength];
            is.readFully (classCode);

            ShadowClassTable.loadClass (
                classInternalName, classLoaderNetReference, classCode
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
