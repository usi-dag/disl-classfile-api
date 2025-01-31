package ch.usi.dag.dislserver;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.objectweb.asm.ClassReader;

import com.google.protobuf.ByteString;

import ch.usi.dag.disl.DiSL;
import ch.usi.dag.disl.DiSL.CodeOption;
import ch.usi.dag.disl.exception.DiSLException;
import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.dislserver.Protocol.InstrumentClassRequest;
import ch.usi.dag.dislserver.Protocol.InstrumentClassResponse;
import ch.usi.dag.util.Strings;
import ch.usi.dag.util.logging.Logger;


final class RequestProcessor {

    private static final Logger __log = Logging.getPackageInstance ();

    //

    private static final String uninstrPath = System.getProperty ("dislserver.uninstrumented");
    private static final String instrPath = System.getProperty ("dislserver.instrumented");
    private static final boolean disableBypass = Boolean.getBoolean ("dislserver.disablebypass");

    //

    private final DiSL __disl;

    //

    private RequestProcessor (final DiSL disl) {
        __disl = disl;
    }

    //

    public InstrumentClassResponse process (final InstrumentClassRequest request) {
        final byte [] classBytes = request.getClassBytes ().toByteArray ();
        final String className = __getClassName (request.getClassNameBytes ().toByteArray (), classBytes);
        final Set <CodeOption> options = CodeOption.setOf (request.getFlags ());

        if (__log.traceIsLoggable ()) {
            __log.trace (
                "instrumenting class %s [%d bytes, %s]",
                className.isEmpty () ? "<unknown>" : className,
                classBytes.length, Strings.join ("+", options)
            );
        }

        //
        // If requested, dump the uninstrumented byte code, instrument the
        // class, and again, if requested, dump the instrumented bytecode.
        // Create a response corresponding to the request and re-throw any
        // exception that might have been thrown as an server internal error.
        //
        try {
            if (uninstrPath != null) {
                __dumpClass (classBytes, className, uninstrPath);
            }

            // TODO: instrument the bytecode according to given options
            // byte [] instrCode = disl.instrument (origCode, options);

            final byte [] newClassBytes = __disl.instrument (classBytes);

            if (newClassBytes != null) {
                if (instrPath != null) {
                    __dumpClass (newClassBytes, className, instrPath);
                }

                return InstrumentClassResponse.newBuilder ()
                    .setResult (Protocol.InstrumentClassResult.CLASS_MODIFIED)
                    .setClassBytes (ByteString.copyFrom (newClassBytes))
                    .build ();

            } else {
                return InstrumentClassResponse.newBuilder ()
                    .setResult (Protocol.InstrumentClassResult.CLASS_UNMODIFIED)
                    .build ();
            }

        } catch (final Exception e) {
            final String message = String.format (
                "error instrumenting %s: %s", className, __getFullMessage (e)
            );

            __log.error (message);

            return InstrumentClassResponse.newBuilder ()
                .setResult (Protocol.InstrumentClassResult.ERROR)
                .setErrorMessage (message)
                .build ();
        }
    }


    private static String __getFullMessage (final Throwable t) {
        final StringWriter result = new StringWriter ();
        t.printStackTrace (new PrintWriter (result));
        return result.toString ();
    }


    private static String __getClassName (
        final byte [] nameBytes, final byte [] classBytes
    ) {
        String result = Strings.EMPTY_STRING;
        if (nameBytes.length > 0) {
            result = new String (nameBytes);
        }

        if (result.isEmpty ()) {
            result = new ClassReader (classBytes).getClassName ();
            if (result == null || result.isEmpty ()) {
                result = UUID.randomUUID ().toString ();
            }
        }

        return result;
    }



    /**
     * Dumps binary representation of a class into a file. The class file is
     * written into a file in a hierarchy of directories corresponding to class
     * package hierarchy, with the top-level directory.
     *
     * @param byteCode
     *        class byte code, must not be {@code null}
     * @param className
     *        class name, must not be empty
     * @param root
     *        the top-level directory of the directory hierarchy into which the
     *        class file will be stored, must not be {@code null}
     * @throws IOException
     */
    private static void __dumpClass (
        final byte[] byteCode, final String className, final String root
    ) throws IOException {
        // Create the package directory hierarchy
        final Path dir = FileSystems.getDefault ().getPath (
            root, JavaNames.typeToInternal (JavaNames.packageName (className))
        );

        Files.createDirectories (dir);

        // Dump the class byte code.
        final Path file = dir.resolve (JavaNames.appendClassFileExtension (
            JavaNames.simpleClassName (className)
        ));

        try (
            final OutputStream os = Files.newOutputStream (file);
        ) {
            os.write (byteCode);
        }
    }

    //

    public void terminate () {
        __disl.terminate ();
    }

    //

    public static RequestProcessor newInstance () throws DiSLException {
        // TODO LB: Configure bypass on a per-request basis.
        if (disableBypass) {
            System.setProperty ("disl.disablebypass", "true");
        }

        final DiSL disl = DiSL.init ();
        return new RequestProcessor (disl);
    }

}
