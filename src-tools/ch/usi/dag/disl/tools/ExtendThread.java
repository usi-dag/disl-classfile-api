package ch.usi.dag.disl.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.*;
import java.lang.reflect.AccessFlag;
import static java.lang.constant.ConstantDescs.CD_boolean;


/**
 * Extends {@link Thread} with a "bypass" variable and writes its new bytecode
 * to a class file in a given directory. This is required to compile DiSL bypass
 * code, which checks the state of the "bypass" variable.
 */
public final class ExtendThread {

    public static void main (final String... args) throws Exception {
        if (args.length < 1) {
            System.err.println ("usage: ExtendThread <output-directory>");
            System.exit (1);
        }

        final File outputDir = new File (args [0]);
        if (!outputDir.isDirectory ()) {
            System.err.printf (
                "error: %s does not exist or is not a directory!\n", outputDir);
            System.exit (1);
        }


        //
        // Extend Thread and dump the new bytecode into the given directory.
        //
        final byte [] extendedThread = __extendThread();
        __writeThread (outputDir, extendedThread);
    }


    private static byte[] __extendThread() throws IOException {
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(Thread.class.getName().replace(".", "/") + ".class");
        if (inputStream == null) {
            throw new RuntimeException("Cannot find Thread class. Error in ExtendThread.java");
        }
        byte[] thread = inputStream.readAllBytes();
        final ClassModel classModel = ClassFile.of().parse(thread);
        return ClassFile.of().build(classModel.thisClass().asSymbol(), classBuilder -> {
            for (ClassElement classElement: classModel) {
                // add the initialization to all the constructors
                if (classElement instanceof MethodModel  methodModel && methodModel.methodName().equalsString("<init>")) {
                    classBuilder.transformMethod(methodModel, (methodBuilder, methodElement) -> {
                        if (methodElement instanceof CodeModel codeModel) {
                            methodBuilder.withCode(codeBuilder -> {
                                codeBuilder.aload(0); // load this
                                codeBuilder.bipush(0);  // load false
                                codeBuilder.putfield(classModel.thisClass().asSymbol(), "bypass", CD_boolean);
                                for (CodeElement element: codeModel) {
                                    codeBuilder.with(element);
                                }
                            });
                        } else {
                            methodBuilder.with(methodElement);
                        }
                    });
                } else {
                    classBuilder.with(classElement);
                }
            }
            // add the field
            classBuilder.withField("bypass", CD_boolean, AccessFlag.PUBLIC.mask());
        });
    }


    private static void __writeThread (
        final File baseDir, final byte [] bytes
    ) throws IOException {
        final Class <Thread> tc = Thread.class;
        final String pkgName = tc.getPackage ().getName ();
        final String dirName = pkgName.replace ('.', File.separatorChar);

        final File outputDir = new File (baseDir, dirName);
        final boolean wasMade = outputDir.mkdirs();

        final String fileName = String.format ("%s.class", tc.getSimpleName ());
        final File outputFile = new File (outputDir, fileName);

        final FileOutputStream fos = new FileOutputStream (outputFile);
        try {
            fos.write (bytes);
        } finally {
            fos.close ();
        }
    }
}
