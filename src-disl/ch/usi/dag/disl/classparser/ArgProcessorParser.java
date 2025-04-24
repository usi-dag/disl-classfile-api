package ch.usi.dag.disl.classparser;

import java.lang.classfile.ClassModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ch.usi.dag.disl.util.ClassFileHelper;

import ch.usi.dag.disl.annotation.Guarded;
import ch.usi.dag.disl.annotation.ProcessAlso;
import ch.usi.dag.disl.coderep.UnprocessedCode;
import ch.usi.dag.disl.exception.GuardException;
import ch.usi.dag.disl.exception.ParserException;
import ch.usi.dag.disl.guard.GuardHelper;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.processor.ArgProcessor;
import ch.usi.dag.disl.processor.ArgProcessorKind;
import ch.usi.dag.disl.processor.ArgProcessorMethod;
import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.util.Strings;


class ArgProcessorParser extends AbstractParser {
    //
    // The key is an ASM type representing the class
    // where the argument processor was defined.
    // TODO I replaced Type with ClassDesc since in this case Type represent the type of a class and not a method,
    //  for methods there is MethodTypeDesc
    private final Map <ClassDesc, ArgProcessor> __processors = new HashMap <> ();


    public Map <ClassDesc, ArgProcessor> initProcessors (final LocalVars localVars) {
        __processors.values ().stream ().unordered ()
            .forEach (processor -> processor.init (localVars));

        return __processors;
    }


    // NOTE: this method can be called many times
    public void parse(final ClassModel dislClass) throws ParserException {
        processLocalVars(dislClass);

        final String className = ClassFileHelper.typeName(dislClass);
        final List<ArgProcessorMethod> methods = __parseMethods(dislClass);
        if (methods.isEmpty ()) {
            throw new ParserException (
                    "argument processor %s has no methods!", className
            );
        }

        __processors.put(dislClass.thisClass().asSymbol(), new ArgProcessor(className, methods));
    }


    private List<ArgProcessorMethod> __parseMethods(final ClassModel dislClass) throws ParserException {
        try {
            final String className = ClassFileHelper.typeName(dislClass);
            return dislClass.methods().parallelStream().unordered()
                    .map(MethodModelCopy::new)
                    .filter(m -> !JavaNames.isConstructorName(m.methodName().stringValue()))
                    .filter(m -> !JavaNames.isInitializerName(m.methodName().stringValue()))
                    .map(m -> __parseMethodWrapper(className, m))
                    .collect(Collectors.toList());
        } catch (final ParserRuntimeException e) {
            throw new ParserException(e.getMessage(), e.getCause());
        }
    }


    private ArgProcessorMethod __parseMethodWrapper(final String className, final MethodModelCopy method) {
        try {
            return __parseMethod(className, method);
        } catch (final Exception e) {
            e.printStackTrace();
            throw new ParserRuntimeException (
                    e, "error parsing argument processor method %s.%s()",
                    className, method.methodName().stringValue()
            );
        }
    }


    private ArgProcessorMethod __parseMethod(final String className, final MethodModelCopy method) throws ParserException, GuardException {
        __ensureArgProcessorIsWellDefined(method);

        // Parse method annotation and ensure that additional parameter types
        // specified in the @ProcessAlso annotation are valid.
        final AnnotationData data = AnnotationData.parse(method);
        final ArgProcessorKind defaultType = ArgProcessorKind.forMethod(method);
        __ensureValidProcessAlsoTypes(defaultType, data.processAlsoType);

        // Determine the set of parameter types accepted by the processor.
        final Set<ArgProcessorKind> processedTypes = EnumSet.of(defaultType);
        processedTypes.addAll(data.processAlsoType);

        // Lookup the guard method in the specified guard class.
        // TODO LB: Consider moving method resolution to annotation processing.
        final Method guardMethod = GuardHelper.findAndValidateGuardMethod(data.guardClass.orElse(null), GuardHelper.processorContextSet());

        // Determine the kinds of contexts used in the processor parameters,
        // and create a code template for the argument processor method.
        final UnprocessedCode codeTemplate = new UnprocessedCode(className, method);

        return new ArgProcessorMethod(processedTypes, guardMethod, codeTemplate);
    }


    private void __ensureArgProcessorIsWellDefined(final MethodModelCopy method) throws ParserException {
        __ensureMethodHasAtLeastOneParameter(method);
        __ensureFirstParameterIsObjectOrPrimitive(method);

        ensureMethodIsStatic(method);
        ensureMethodReturnsVoid(method);
        ensureMethodThrowsNoExceptions(method);

        ensureMethodIsNotEmpty(method);
        ensureMethodUsesContextProperly(method);
    }


    private static void __ensureMethodHasAtLeastOneParameter(final MethodModelCopy methodModel) throws ParserException {
        if (methodModel.methodTypeSymbol().parameterCount() < 1) {
            throw new ParserException ("method has no parameters!");
        }
    }


    private static void __ensureFirstParameterIsObjectOrPrimitive(final MethodModelCopy method) throws ParserException {
        ClassDesc argType = method.methodTypeSymbol().parameterType(0);
        TypeKind typeKind = TypeKind.fromDescriptor(argType.descriptorString());
        // TODO is this equivalent to the asm version???
        if (argType.isPrimitive() && !typeKind.equals(TypeKind.VOID)) {
            return; // primitive are accepted except void
        }
        if (argType.isArray()) {
            return; // array are accepted
        }
        if (argType.descriptorString().equals(Object.class.descriptorString())) {
            return; // type Object is accepted
        }
        throw new ParserException (
                "type of the first argument must be Object or a non-void "+
                        "primitive type, found %s!", argType.displayName()
        );
    }

    //

    private static class AnnotationData {
        //
        // The default values are meant for annotations without defaults.
        // These require the user to specify a value when the annotation is
        // used, but we need to provide sane values upstream even when the
        // annotation is not used.
        //
        private EnumSet <ArgProcessorKind> processAlsoType =
            EnumSet.noneOf (ArgProcessorKind.class);

        private Optional <Class <?>> guardClass = Optional.empty ();

        //

        public static AnnotationData parse(final MethodModelCopy method) {
            final AnnotationData result = new AnnotationData();

            new AnnotationMapper()
                    .register(Guarded.class, "guard", (n, v) -> {
                        if (v instanceof ClassDesc) {
                            result.guardClass = Optional.of(__resolveClass((ClassDesc) v));
                        }
                    })
                    .register(ProcessAlso.class, "types", (n, v) -> {
                        if (v instanceof List<?>) {
                            // TODO is this correct ???????????
                            @SuppressWarnings ("unchecked")
                            final List <ProcessAlso.Type> values = (List <ProcessAlso.Type>) v;
                            result.processAlsoType = __toArgProcessorKindSet (values);
                        }
                    })
                    .processDefaults()
                    .accept(method);

            return result;
        }



        private static Class<?> __resolveClass(final ClassDesc classDesc) {
            try {
                return Class.forName(ClassFileHelper.getClassName(classDesc));
            } catch (final ClassNotFoundException e) {
                throw new ParserRuntimeException(e);
            }
        }


        private static EnumSet <ArgProcessorKind> __toArgProcessorKindSet (
            final List <ProcessAlso.Type> types
        ) {
            return EnumSet.copyOf (
                types.stream ().map (t -> {
                    if (ProcessAlso.Type.BOOLEAN.equals (t)) {
                        return ArgProcessorKind.BOOLEAN;
                    } else if (ProcessAlso.Type.BYTE.equals (t)) {
                        return ArgProcessorKind.BYTE;
                    } else if (ProcessAlso.Type.SHORT.equals (t)) {
                        return ArgProcessorKind.SHORT;
                    } else {
                        throw new ParserRuntimeException ("unexpected ProcessAlso.Type instance: "+ t);
                    }
                }).collect (Collectors.toSet ())
            );
        }
    }

    //

    private void __ensureValidProcessAlsoTypes (
        final ArgProcessorKind primaryType,
        final EnumSet <ArgProcessorKind> additionalTypes
    ) throws ParserException {
        final Set <ArgProcessorKind> invalidTypes = additionalTypes.clone ();
        invalidTypes.removeAll (primaryType.secondaryTypes ());

        if (! invalidTypes.isEmpty ()) {
            throw new ParserException(
                "%s argument processor cannot process [%s]",
                primaryType, Strings.join (",", invalidTypes)
            );
        }
    }

}
