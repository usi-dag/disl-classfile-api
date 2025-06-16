package ch.usi.dag.disl.coderep;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import ch.usi.dag.disl.util.*;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;

import ch.usi.dag.disl.InitializationException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.localvar.AbstractLocalVar;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;

import static ch.usi.dag.disl.util.ClassFileHelper.getLabelTargetMap;


/**
 * Represents a snippet or argument processor code template. This template only
 * contains the original code, without any modifications related, e.g., to the
 * use of thread local variables. An instance of the template containing the
 * modified/processed code can be obtained on demand using the
 * {@link #process(LocalVars) process()} method.
 */
public class UnprocessedCode {

    /** Canonical name of the class where the snippet was defined. */
    private final String __className;

    /** Method node containing the snippet code. */
    private final MethodModelCopy __method;

    public UnprocessedCode(final String className, final MethodModelCopy methodModel) {
        __className = className;
        __method = methodModel;
    }

    public String className () {
        return __className;
    }

    public String methodName () {
        return __method.methodName().stringValue();
    }

    public String location () {
        if (!__method.hasCode()) {
            throw new RuntimeException("Method " + __method.methodName().stringValue() + " Has no Code!");
        }
        return location (__method.instructions().getFirst());
    }

    public String location(final CodeElement codeElement) {
        if (!__method.hasCode()) {
            throw new RuntimeException("Method " + __method.methodName().stringValue() + " Has no Code!");
        }
        return String.format (
                "snippet %s.%s%s", __className, __method.methodName().stringValue(),
                ClassFileHelper.formatLineNo(":%d ", codeElement, __method.instructions())
        );
    }

    //

    public Code process (final LocalVars vars) {
        // Analyze code:
        //
        // Determine the kinds of contexts used in the snippet parameters.
        //
        // Collect a set of static context methods invoked by the snippet code.
        // This is done first, because it can result in initialization failure.
        //
        // Then collect the sets of referenced synthetic local and thread local variables, and finally determine if there is any exception handler in
        // the code that handles the exception and does not propagate it.
        final ContextUsage ctxs = ContextUsage.forMethod(__method);
        List<CodeElement> instructions = __method.instructions();

        final Set<StaticContextMethod> staticContextMethods = __collectStaticContextMethods(
                instructions, ctxs.staticContextTypes()
        );

        final Set<SyntheticLocalVar> syntheticLocalVars = __collectReferencedVars(
                instructions, vars.getSyntheticLocals()
        );

        final Set<ThreadLocalVar> threadLocalVars = __collectReferencedVars(
                instructions, vars.getThreadLocals()
        );

        // in the ClassFile api the exception block instruction are included also with the normal instruction
        List<CodeElement> instructionsWithoutTryCatch = instructions.stream().filter(i -> !(i instanceof ExceptionCatch)).collect(Collectors.toList());
        List<ExceptionCatch> exceptionCatches = __method.hasCode()? __method.exceptionHandlers() : new ArrayList<>();

        final boolean handlesExceptions = __handlesExceptionWithoutThrowing (
            instructionsWithoutTryCatch, exceptionCatches
        );

        // Process code:
        // - replace all RETURN instructions with a GOTO to the end of a method
        // - In this function also all PseudoInstruction that represents lines and local variable
        //   are removed, if that were not the case they might conflict with the local variable
        //   of the code to be instrumented
        final MethodModelCopy method = ClassFileCodeTransformer.replaceReturnsWithGoto(__method);

        return new Code (
            method, syntheticLocalVars, threadLocalVars, staticContextMethods, handlesExceptions
        );
    }


    /**
     * Collects instances of unique static method invocations from the given
     * list of byte code instructions. Throws an exception if any of the
     * invocations is invalid.
     *
     * @param instructions
     *        instructions to analyze
     * @param scTypes
     *        a set of known static context types
     * @return A set of {@link StaticContextMethod} instances.
     * @throws InitializationException
     *         if the static context method invocation is invalid, i.e., it
     *         contains arguments, has an invalid return type, or any of the
     *         referenced classes or methods could not be found via reflection
     */
    // TODO is this correct????
    private Set<StaticContextMethod> __collectStaticContextMethods(final List<CodeElement> instructions, final Set<ClassDesc> scTypes) {
        try {
            final ConcurrentMap <String, Boolean> seen = new ConcurrentHashMap <> ();
            return instructions.parallelStream().unordered()
                    // Select instructions representing method invocations on known static context classes.
                    .filter(instruction -> instruction instanceof InvokeInstruction)
                    .map(instruction -> (InvokeInstruction)instruction)
                    .filter(instruction -> scTypes.contains(instruction.owner().asSymbol()))
                    // Ensure that each static context method invocation is valid.
                    // This means that it does not have any parameters and only returns either a primitive type or a String.
                    .filter(instruction -> {
                        __ensureInvocationHasNoArguments(instruction);
                        __ensureInvocationReturnsAllowedType(instruction);
                        return true;
                    })
                    // Finally create an instance of static method invocation, but only for methods we have not seen so far.
                    .filter(instruction -> seen.putIfAbsent(__methodId(instruction), true) == null)
                    .map(instruction -> {
                        final Class<?> ownerClass = __resolveClass(instruction);
                        final Method contextMethod = __resolveMethod(instruction, ownerClass);
                        return new StaticContextMethod(
                                __methodId(instruction), contextMethod, ownerClass
                        );
                    })
                    .collect(Collectors.toSet());
        } catch (final InvalidStaticContextInvocationException e) {
            final InvokeInstruction instruction = e.insn();
            throw new InitializationException(
                    "%s: invocation of static context method %s.%s: %s",
                    location(instruction), JavaNames.internalToType(instruction.owner().asInternalName()),
                    instruction.name().stringValue(), e.getMessage()
            );
        }
    }

    private static String __methodId (final InvokeInstruction instruction) {
        return JavaNames.methodName (instruction.owner().name().stringValue(), instruction.name().stringValue());
    }

    private void __ensureInvocationHasNoArguments(final InvokeInstruction instruction) {
        if (!instruction.typeSymbol().parameterList().isEmpty()) {
            throw new InvalidStaticContextInvocationException (
                    "arguments found, but NONE allowed", instruction
            );
        }
    }

    private void __ensureInvocationReturnsAllowedType (final InvokeInstruction instruction) {
        final ClassDesc returnType = instruction.typeSymbol().returnType();
        if (! __ALLOWED_RETURN_TYPES__.contains (returnType)) {
            throw new InvalidStaticContextInvocationException (
                "return type MUST be a primitive type or a String", instruction
            );
        }
    }

    private static final Set <ClassDesc> __ALLOWED_RETURN_TYPES__ = Collections.unmodifiableSet (
            new HashSet<>(9) {{
                add(ClassDesc.ofDescriptor(boolean.class.descriptorString()));
                add(ClassDesc.ofDescriptor(byte.class.descriptorString()));
                add(ClassDesc.ofDescriptor(char.class.descriptorString()));
                add(ClassDesc.ofDescriptor(short.class.descriptorString()));
                add(ClassDesc.ofDescriptor(int.class.descriptorString()));
                add(ClassDesc.ofDescriptor(long.class.descriptorString()));
                add(ClassDesc.ofDescriptor(float.class.descriptorString()));
                add(ClassDesc.ofDescriptor(double.class.descriptorString()));
                add(ClassDesc.ofDescriptor(String.class.descriptorString()));
            }}
    );


    private Class <?> __resolveClass (final InvokeInstruction instruction) {
        try {
            return ReflectionHelper.resolveClass(instruction.owner().asSymbol());

        } catch (final ReflectionException e) {
            throw new InvalidStaticContextInvocationException (e.getMessage (), instruction);
        }
    }


    private Method __resolveMethod (
        final InvokeInstruction instruction, final Class <?> ownerClass
    ) {
        try {
            return ReflectionHelper.resolveMethod (ownerClass,  instruction.name().stringValue());

        } catch (final ReflectionException e) {
            throw new InvalidStaticContextInvocationException (e.getMessage (), instruction);
        }
    }


    /**
     * Scans the given instruction sequence for field accesses and collects a
     * set of special local variables referenced by instructions in the
     * sequence. The local variables are identified by a fully qualified field
     * name.
     *
     * @param <T> type of the return value
     * @param instructions the instructions list to scan
     * @param vars mapping between fully qualified field names and variables
     * @return a set of variables references by the code.
     */
    private <T> Set<T> __collectReferencedVars(final List<CodeElement> instructions, final Map<String, T> vars) {
        return instructions.parallelStream().unordered()
                .filter(instruction -> instruction instanceof FieldInstruction)
                .map(instruction -> {
                    final FieldInstruction fieldInstruction = (FieldInstruction) instruction;
                    return Optional.ofNullable(vars.get(
                            // TODO "asInternalName() is equivalent????
                            AbstractLocalVar.fqFieldNameFor(fieldInstruction.owner().asInternalName(), fieldInstruction.name().stringValue())
                    ));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    /**
     * Determines if the code contains an exception handler that handles
     * exceptions and does not propagate them further. This has to be detected
     * because it can cause stack inconsistency that has to be handled in the
     * weaver.
     */
    private boolean __handlesExceptionWithoutThrowing(final List<CodeElement> instructions, List<ExceptionCatch> exceptionCatches) {
        if (exceptionCatches.isEmpty()) {
            return false;
        }
        // Create a control flow graph and check if the control flow continues
        // after an exception handler, which indicates that the handler handles the exception.
        final ControlFlowGraph cfg = new ControlFlowGraph(instructions, exceptionCatches);
        cfg.visit(instructions.stream()
                .filter(i -> !(i instanceof ExceptionCatch))
                .findFirst().orElseThrow());  // TODO this should not happen, but maybe it should be handled with a custom DiSL exception

        Map<Label, CodeElement> labelTargetMap = getLabelTargetMap(instructions);

        for (int i = exceptionCatches.size() - 1; i >= 0; i--) {
            final ExceptionCatch exceptionCatch = exceptionCatches.get(i);
            Label label = exceptionCatch.handler();
            if (labelTargetMap.containsKey(label)) {
                List<CodeElement> visited = cfg.visit(labelTargetMap.get(label));
                if (!visited.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

}
