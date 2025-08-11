package ch.usi.dag.disl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Native;
import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.stream.Collectors;
import ch.usi.dag.disl.classparser.DislClasses;
import ch.usi.dag.disl.exception.DiSLException;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.disl.processor.generator.PIResolver;
import ch.usi.dag.disl.processor.generator.ProcGenerator;
import ch.usi.dag.disl.processor.generator.ProcInstance;
import ch.usi.dag.disl.processor.generator.ProcMethodInstance;
import ch.usi.dag.disl.scope.Scope;
import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Snippet;
import ch.usi.dag.disl.staticcontext.generator.SCGenerator;
import ch.usi.dag.disl.util.*;
import ch.usi.dag.disl.weaver.Weaver;
import ch.usi.dag.util.logging.Logger;

import static java.lang.constant.ConstantDescs.CD_boolean;


/**
 * Provides an entry-point and a simple interface to the DiSL instrumentation
 * framework. This interface is primarily used by the DiSL instrumentation
 * server, but is generally intended for any instrumentation tool wishing to use
 * DiSL.
 */
public final class DiSL {

    private final Logger __log = Logging.getPackageInstance ();

    private final boolean debug = Boolean.getBoolean ("debug");

    //

    private final Set <CodeOption> __codeOptions;

    private final Transformers __transformers;

    private final Set <Scope> __excludedScopes;

    private final DislClasses __dislClasses;

    // TODO make these variables set by a config or a parameter passed at application launch
    private boolean verifyClass = false;
    private boolean loadExtraClasses = false;
    private boolean dropStackMap = false;


    /**
     * Initializes a DiSL instance by loading transformers, exclusion lists, and
     * DiSL classes.
     * <p>
     * <b>Note:</b> This constructor is deprecated and will be removed in later
     * releases. Use the {@link #init()} static factory method to obtain a
     * {@link DiSL} instance.
     *
     * @param useDynamicBypass
     *        determines whether to generate bypass code and whether to control
     *        the bypass dynamically.
     */
    @Deprecated
    public DiSL (final boolean useDynamicBypass) throws DiSLException {
        final Properties properties = System.getProperties ();
        __codeOptions = __codeOptionsFrom (
            Objects.requireNonNull (properties)
        );

        // Add the necessary bypass options for backwards compatibility.
        if (useDynamicBypass) {
            __codeOptions.add (CodeOption.CREATE_BYPASS);
            __codeOptions.add (CodeOption.DYNAMIC_BYPASS);
        }

        final ClassResources resources = ClassResources.discover (properties);
        __transformers = Transformers.load (resources.transformers ());
        __excludedScopes = ExclusionSet.prepare (resources.instrumentationResources ());
        __dislClasses = DislClasses.load (__codeOptions, resources.dislClasses ());
    }


    /**
     * Initializes a DiSL instance.
     */
    private DiSL (
        final Set <CodeOption> codeOptions, final Transformers transformers,
        final Set <Scope> excludedScopes, final DislClasses dislClasses
    ) {
        __codeOptions = codeOptions;
        __transformers = transformers;
        __excludedScopes = excludedScopes;
        __dislClasses = dislClasses;
    }


    /**
     * Creates a {@link DiSL} instance. This involves loading and parsing DiSL
     * classes containing snippets, loading transformer classes, and setting up
     * exclusion lists. This method uses the system properties to look for
     * configuration settings.
     *
     * @return A {@link DiSL} instance.
     * @throws DiSLException
     *         if the initialization failed.
     */
    public static DiSL init () throws DiSLException {
        return init (System.getProperties ());
    }


    /**
     * Loads transformers, exclusion lists, and DiSL classes with snippets, and
     * creates an instance of the {@link DiSL} class.
     *
     * @param properties
     *        the properties to use in place of system properties, may not be
     *        {@code null}
     * @return A {@link DiSL} instance.
     * @throws DiSLException
     *         if the initialization failed.
     */
    private static DiSL init (final Properties properties) throws DiSLException {
        final Set <CodeOption> codeOptions = __codeOptionsFrom (
            Objects.requireNonNull (properties)
        );

        final ClassResources resources = ClassResources.discover (properties);
        final Transformers transformers = Transformers.load (resources.transformers ());
        final Set <Scope> excludedScopes = ExclusionSet.prepare (resources.instrumentationResources ());
        final DislClasses dislClasses = DislClasses.load (codeOptions, resources.dislClasses ());

        //resources.serialize("serialized.txt");

        // TODO put checker here
        // like After should catch normal and abnormal execution
        // but if you are using After (AfterThrowing) with BasicBlockMarker
        // or InstructionMarker that doesn't throw exception, then it is
        // probably something, you don't want - so just warn the user
        // also it can warn about unknown opcodes if you let user to
        // specify this for InstructionMarker

        return new DiSL (codeOptions, transformers, excludedScopes, dislClasses);
    }


    /**
     * Derives code options from global properties. This is a transitional
     * compatibility method for the transition to per-request code options.
     */
    public static Set <CodeOption> __codeOptionsFrom (
        final Properties properties
    ) {
        final Set <CodeOption> result = EnumSet.noneOf (CodeOption.class);

        final boolean useExceptHandler = !__getBoolean ("disl.noexcepthandler", properties);
        if (useExceptHandler) {
            result.add (CodeOption.CATCH_EXCEPTIONS);
        }

        final boolean disableBypass = __getBoolean ("disl.disablebypass", properties);
        if (!disableBypass) {
            result.add (CodeOption.CREATE_BYPASS);
            result.add (CodeOption.DYNAMIC_BYPASS);
        }

        final boolean splitLongMethods = __getBoolean ("disl.splitmethods", properties);
        if (splitLongMethods) {
            result.add (CodeOption.SPLIT_METHODS);
        }

        return result;
    }

    private static boolean __getBoolean (
        final String name, final Properties properties
    ) {
        return Boolean.parseBoolean (properties.getProperty(name));
    }


    /**
     * Instruments a method in a class.
     *
     * @param classModel
     *        class that will be instrumented
     * @param methodModel
     *        method in the classModel argument, that will be instrumented
     * @param classBuilder
     *        the builder that will build the instrumented class
     * @return {@code true} if the methods was changed, {@code false} otherwise.
     */
    private boolean instrumentMethod(ClassModel classModel, MethodModelCopy methodModel, ClassBuilder classBuilder) throws DiSLException {
        if (methodModel.flags().has(AccessFlag.ABSTRACT) ||
                methodModel.flags().has(AccessFlag.NATIVE)
        ) {
            return false; // skip abstract and native methods
        }

        final String className = classModel.thisClass().name().stringValue();
        final String methodName = methodModel.methodName().stringValue();
        final String methodDesc = methodModel.methodTypeSymbol().descriptorString();

        // evaluate exclusions  TODO LB: Add support for inclusion
        final Optional <Scope> excludeMatch = __excludedScopes.stream ()
                .filter (ex -> ex.matches (className, methodName, methodDesc))
                .findFirst ();

        if (excludeMatch.isPresent ()) {
            __log.debug ("excluded %s.%s%s via %s", className, methodName, methodDesc, excludeMatch.get ());
            return false;
        }

        // Find snippets with a scope matching the class and methods being instrumented.
        // If there are no such snippets, there is nothing to instrument, and we can bail out early.
        final List <Snippet> matchingSnippets = __dislClasses.selectMatchingSnippets (
                className, methodName, methodDesc
        );
        if (matchingSnippets.isEmpty ()) {
            __log.debug ("skipping unaffected method: %s.%s%s",
                    className, methodName, methodDesc);
            return false;
        }

        // Apply markers to class methods to receive a list of shadows which represent the individual instances of a snippet.
        // Filter the initial list of shadows through guards and collect snippets that have at least one applicable shadow.
        final Map<Snippet, List<Shadow>> applicableSnippets = new HashMap <> ();
        for (final Snippet snippet: matchingSnippets) {
            __log.trace ("\tsnippet: %s.%s()", snippet.getOriginClassName (), snippet.getOriginMethodName ());

            final List<Shadow> applicableShadows = snippet.selectApplicableShadows(classModel, methodModel);
            __log.trace ("\tapplicable shadows: %d", applicableShadows.size ());

            if (!applicableShadows.isEmpty()) {
                applicableSnippets.put(snippet, applicableShadows);
            }

        }

        // *** compute static info ***
        __log.trace ("calculating static information for method: %s.%s%s",
                className, methodName, methodDesc);

        // prepares SCGenerator class (computes static context)
        final SCGenerator staticInfo = SCGenerator.computeStaticInfo (applicableSnippets);

        // *** used synthetic and thread-local vars in snippets ***
        __log.trace ("finding locals used by method: %s.%s%s",
                className, methodName, methodDesc);

        final Set <SyntheticLocalVar> usedSLVs = __collectReferencedSLVs (applicableSnippets.keySet ());
        final Set <ThreadLocalVar> usedTLVs = __collectReferencedTLVs (applicableSnippets.keySet ());

        // *** prepare processors ***
        __log.trace ("preparing argument processors for method: %s.%s%s",
                className, methodName, methodDesc);

        final PIResolver piResolver = new ProcGenerator().compute(applicableSnippets);

        // *** used synthetic local vars in processors ***

        // include SLVs from processor methods into usedSLV
        for (final ProcInstance pi : piResolver.getAllProcInstances ()) {
            for (final ProcMethodInstance pmi : pi.getMethods ()) {
                usedSLVs.addAll (pmi.getCode ().getReferencedSLVs ());
            }
        }

        // *** weaving ***

        if (!applicableSnippets.isEmpty()) {
            __log.debug ("found %d snippet marking(s), weaving method: %s.%s%s",
                    applicableSnippets.size (), className, methodName, methodDesc);

            Weaver.instrument (
                    classModel, methodModel, classBuilder, applicableSnippets,
                    usedSLVs, usedTLVs, staticInfo, piResolver
            );

            return true;

        } else {
            __log.debug ("found %d snippet marking(s), skipping method: %s.%s%s",
                    0, className, methodName, methodDesc);

            return false;
        }
    }


    /**
     * Collects a list of synthetic local variables that are actively
     * used in the selected (matched) snippets.
     */
    private Set <SyntheticLocalVar> __collectReferencedSLVs (
        final Collection <Snippet> snippets
    ) {
        //
        // Uses LinkedHashSet to maintain iteration order.
        //
        return snippets.stream ().unordered ()
            .flatMap (s -> s.getCode ().getReferencedSLVs ().stream ())
            .collect (LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }


    /**
     * Collects a list of thread-local variables that are actively
     * used in the selected (matched) snippets.
     */
    private Set <ThreadLocalVar> __collectReferencedTLVs (
        final Collection <Snippet> snippets
    ) {
        return snippets.stream ().unordered ()
            .flatMap (s -> s.getCode ().getReferencedTLVs ().stream ())
            .collect (Collectors.toSet ());
    }


    /**
     * Data holder for an instrumented class
     */
    private static class InstrumentedClass {
        final Set <String> changedMethods;

        final ClassModel originalClassModel;
        byte[] instrumentedClassBytes;

        public InstrumentedClass(ClassModel original, final Set<String> changedMethods, byte[] instrumentedClass) {
            this.originalClassModel = original;
            this.changedMethods = changedMethods;
            this.instrumentedClassBytes = instrumentedClass;
        }
    }


    /**
     * Instruments class model.
     * Note: This method is thread safe. Parameter classNode is changed during
     * the invocation.
     *
     * @param classModel
     *            class model to instrument
     * @return instrumented class
     */
    private InstrumentedClass instrumentClass(ClassModel classModel) throws DiSLException {
        Set<ClassFile.Option> options = new HashSet<>();
        if (loadExtraClasses) {
            // TODO for now the path to extra classes are hard-coded, need to change so that they can be loaded at start-up with some arguments
            options.add(ClassLoaderFromJar.getTestResOption());
        }
        if (dropStackMap) {
            options.add(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        }
        final Set <String> changedMethods = new HashSet<>();
        // TODO also here might use ClassModelHelper to pass some options
        try {
            byte[] instrumentedClass = ClassFile.of(options.toArray(ClassFile.Option[]::new)).build(classModel.thisClass().asSymbol(), classBuilder -> {
                for (ClassElement classElement: classModel) {
                    if (Objects.requireNonNull(classElement) instanceof MethodModel methodModel) {
                        boolean methodChanged;

                        MethodModelCopy methodModelCopy = new MethodModelCopy(methodModel);
                        try {
                            methodChanged = instrumentMethod(classModel, methodModelCopy, classBuilder);
                        } catch (DiSLException e) {
                            throw new RuntimeException(e);
                        }

                        if (!methodChanged) {
                                classBuilder.with(methodModel); // if the method was not changed then add it back like the original
                        } else {
                            changedMethods.add(ClassFileHelper.nameAndDescriptor(methodModel));
                        }

                    } else {
                        classBuilder.with(classElement); // add all other elements as the original
                    }
                }
            });

            byte[] instrumentedThread = null;
            if (classModel.thisClass().asSymbol().descriptorString().equals(Thread.class.descriptorString())) {
                // If the instrumented class is the Thread class, add fields that
                // will provide thread-local variables to the code in the snippets.
                ClassModel instrumented = ClassFile.of().parse(instrumentedClass);

                final Set<ThreadLocalVar> tlvs = __collectReferencedTLVs(__dislClasses.getSnippets());
                if (__codeOptions.contains(CodeOption.DYNAMIC_BYPASS)) {
                    tlvs.add(__createBypassTlv());
                }

                instrumentedThread = ClassFile.of().build(instrumented.thisClass().asSymbol(), classBuilder -> {

                    for (ThreadLocalVar tlv: tlvs) {
                        // add a new field for each tlv
                        classBuilder.withField(tlv.getName(), ClassDesc.ofDescriptor(tlv.getDescriptor()), AccessFlag.PUBLIC.mask());
                    }

                    for (ClassElement classElement: instrumented) {
                        if (classElement instanceof MethodModel methodModel && JavaNames.isConstructorName(methodModel.methodName().stringValue())) {
                            // if is a constructor add the initialization code
                            ClassFileTLVInserter.insertThreadLocalVariables(tlvs, classBuilder, methodModel);
                        } else {
                            classBuilder.with(classElement);
                        }
                    }
                });
            }

            boolean classChanged = !changedMethods.isEmpty();

            if (instrumentedThread != null && instrumentedThread.length > 0) {
                classChanged = true;
                instrumentedClass = instrumentedThread;
            }

            return classChanged? new InstrumentedClass(classModel, changedMethods, instrumentedClass): null;
        } catch (Exception e) {
//            WriteInfo info = WriteInfo.getInstance();
//            if (e.getMessage() == null) {
//                info.writeLine("An exception occurred: somehow the message from the exception is null ");
//                throw new RuntimeException(e);
//            }
//            List<VerifyError> errors = ClassFile.of().verify(classModel);
//            info.writeLine(">>>>>> Exception in instrumentClass for class: " + classModel.thisClass().name() + " the class has: " + errors.size() + " verifyError");
//            info.writeLine("Message: " + e.getMessage());
////            info.writeLine(String.valueOf(e.getClass()));
//            for ( StackTraceElement element: e.getStackTrace()) {
//                info.writeLine(element.toString());
//            }
//            info.writeLine("<<< Exception end");

//            info.writeLine(">>> Verify Errors for class: " + classModel.thisClass().name());
//            for (VerifyError error: errors) {
//                info.writeLine(error.getMessage());
//            }
//            info.writeLine("<<< End list of VerifyErrors");
            throw new RuntimeException(e);
        }
    }

    private ThreadLocalVar __createBypassTlv () {
        // prepare dynamic bypass thread local variable

        return new ThreadLocalVar (
            null, "bypass", CD_boolean, false
        );
    }


    /**
     * Instruments the given class, provided as an array of bytes representing
     * the contents of its class file.
     *
     * @param originalBytes
     *        the class to instrument as an array of bytes
     * @return An array of bytes representing the instrumented class, or
     *         {@code null} if the class has not been instrumented.
     */
    // TODO ! current static context interface does not allow to have nice
    // synchronization - it should be redesigned such as the staticContextData
    // also invokes the required method and returns result - if this method
    // (and static context class itself) will be synchronized, it should work
    public synchronized byte [] instrument (
        final byte [] originalBytes
    ) throws DiSLException {
        if (debug) {
            // keep the currently processed class around in case of errors
            __dumpBytesToFile (originalBytes, "err.class");
        }

        final byte [] transformedBytes = __transformers.apply(originalBytes); // this is done by the user

        // TODO might use ClassModelHelper to pass some options instead of this
        //  also for now the lines will need to be dropped since there can be cases where
        //  there can be multiples lines represented by the same object
        final ClassModel classModel = ClassFile.of(ClassFile.LineNumbersOption.DROP_LINE_NUMBERS, ClassLoaderFromJar.getTestResOption()).parse(transformedBytes);

        //WriteInfo info = WriteInfo.getInstance();

        Reflection.systemClassLoader ().notifyClassLoaded(classModel);

        if (verifyClass) {
            List<VerifyError> verifyErrors = ClassFile.of().verify(classModel);
            if (!verifyErrors.isEmpty()) {
//                info.writeLine("Class: " + classModel.thisClass().name() + " has " + verifyErrors.size() + " verify error/s");
//                for (VerifyError verifyError: verifyErrors) {
//                    info.writeLine("    err: " + verifyError.getMessage());
//                }
//                info.writeLine(">>>>>>> Excluded class " + classModel.thisClass().name());
                __log.debug ("excluded class: %s because of verifyErrors", classModel.thisClass().name().stringValue());
                return null;
            }
        }

        // Instrument the class. If the class is modified neither by DiSL,
        // nor by any of the transformers, bail out early and return NULL
        // to indicate that the class has not been modified in any way.
        final InstrumentedClass instResult = instrumentClass(classModel);
        if (instResult == null && transformedBytes == originalBytes) {
            return null;
        }

        final ClassModel originalClassModel = ClassFile.of().parse(transformedBytes);
        byte[] instrumentedBytes = instResult.instrumentedClassBytes;

        // If creating bypass code is requested, merge the original method code
        // with the instrumented method code and create code to switch between
        // the two versions based on the result of a bypass check.
        if (__codeOptions.contains (CodeOption.CREATE_BYPASS)) {
            ClassModel instrumentedClass = ClassFile.of().parse(instrumentedBytes);
            instrumentedBytes = CodeMerger.mergeOriginalCode(originalClassModel, instrumentedClass, instResult.changedMethods);
        }


        //
        // Fix-up methods that have become too long due to instrumentation.
        byte [] fixed = CodeMerger.fixLongMethods(ClassFile.of().parse(instrumentedBytes), originalClassModel);
        if (fixed != null) {
            instrumentedBytes = fixed;
        }

        return instrumentedBytes;
    }


    private void __dumpBytesToFile (
        final byte [] classBytes, final String fileName
    ) {
        try {
            final FileOutputStream fos = new FileOutputStream (fileName);
            try {
                fos.write (classBytes);
            } finally {
                fos.close ();
            }
        } catch (final IOException ioe) {
            __log.warn (
                ioe, "failed to dump class bytes to %s", fileName
            );
        }
    }


    /**
     * Termination handler - should be invoked by the instrumentation framework.
     */
    public void terminate () {
        // currently empty
    }


    //

    /**
     * Options for code transformations performed by DiSL.
     */
    public enum CodeOption {

        /**
         * Create a copy of the original method code and check whether to
         * execute the instrumented or the uninstrumented version of the code
         * upon method entry.
         */
        CREATE_BYPASS (Flag.CREATE_BYPASS),

        /**
         * Insert code for dynamic bypass control. Enable bypass when entering
         * instrumentation code and disable it when leaving it.
         */
        DYNAMIC_BYPASS (Flag.DYNAMIC_BYPASS),

        /**
         * Split methods exceeding the limit imposed by the class file format.
         */
        SPLIT_METHODS (Flag.SPLIT_METHODS),

        /**
         * Wrap snippets in exception handlers to catch exceptions. This is
         * mainly useful for debugging instrumentation code, because the
         * handlers terminate the program execution.
         */
        CATCH_EXCEPTIONS (Flag.CATCH_EXCEPTIONS);


        /**
         * Flags corresponding to individual code options. The flags are
         * used when communicating with DiSL agent.
         */
        public interface Flag {
            @Native
            static final int CREATE_BYPASS = 1 << 0;
            @Native
            static final int DYNAMIC_BYPASS = 1 << 1;
            @Native
            static final int SPLIT_METHODS = 1 << 2;
            @Native
            static final int CATCH_EXCEPTIONS = 1 << 3;
        }

        //

        private final int __flag;

        private CodeOption (final int flag) {
            __flag = flag;
        }

        //

        /**
         * Creates a set of code options from an array of options.
         */
        public static Set <CodeOption> setOf (final CodeOption... options) {
            final EnumSet <CodeOption> result = EnumSet.noneOf (CodeOption.class);
            result.addAll(Arrays.asList(options));

            return result;
        }


        /**
         * Creates a set of code options from flags in an integer.
         */
        public static Set <CodeOption> setOf (final int flags) {
            final EnumSet <CodeOption> result = EnumSet.noneOf (CodeOption.class);
            for (final CodeOption option : CodeOption.values ()) {
                if ((flags & option.__flag) != 0) {
                    result.add (option);
                }
            }

            return result;
        }
    }

}
