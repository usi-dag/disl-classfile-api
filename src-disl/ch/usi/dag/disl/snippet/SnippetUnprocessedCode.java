package ch.usi.dag.disl.snippet;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ch.usi.dag.disl.coderep.ClassFileCodeTransformer;
import ch.usi.dag.disl.util.ClassFileHelper;

import ch.usi.dag.disl.DiSL.CodeOption;
import ch.usi.dag.disl.coderep.Code;
import ch.usi.dag.disl.coderep.UnprocessedCode;
import ch.usi.dag.disl.exception.ProcessorException;
import ch.usi.dag.disl.exception.ReflectionException;
import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.marker.Marker;
import ch.usi.dag.disl.processor.ArgProcessor;
import ch.usi.dag.disl.processorcontext.ArgumentProcessorContext;
import ch.usi.dag.disl.processorcontext.ArgumentProcessorMode;


/**
 * Represents a snippet code template. This template contains the original code
 * produced by the Java compiler, potentially wrapped with dynamic bypass
 * control code and an exception handler to catch all exceptions.
 */
public class SnippetUnprocessedCode {
    /**
     * Determines whether the snippet should control dynamic bypass if the
     * dynamic bypass is enabled.
     */
    private final boolean __snippetDynamicBypass;

    /**
     * The code template we are decorating.
     */
    private final UnprocessedCode __template;

    //

    /**
     * Initializes a snippet code template with information about source class,
     * source method, usage of context parameters in the template, and whether
     * the snippet requires automatic control of dynamic bypass.
     */
    public SnippetUnprocessedCode(final String className, final MethodModel method, final boolean snippetDynamicBypass) {
        __template = new UnprocessedCode(className, method);
        __snippetDynamicBypass = snippetDynamicBypass;
    }

    //

    public String className () {
        return __template.className ();
    }


    public String methodName () {
        return __template.methodName ();
    }

    //

    /**
     * Processes the stored data and creates snippet code structure.
     */
    public SnippetCode process(final LocalVars vars, final Map<ClassDesc, ArgProcessor> processor, final Marker marker, final Set<CodeOption> options)
            throws ProcessorException, ReflectionException {
        // Pre-process code with local variables.
        final Code code = __template.process(vars);

        // Process code:
        //
        // - reclaim local variable slots taken by snippet context parameters
        //
        //   The context parameters to a snippet take up local variable slots.
        //   These are not used at runtime, needlessly increasing the stack
        //   frame. To reclaim these slots, we shift all local variable
        //   accesses by the amount of slots occupied by the context
        //   parameters.
        //
        // - If required, insert dynamic bypass control around the snippet.
        // - If required, catch all exceptions around the snippet.
        //
        // Code processing has to be done before looking for argument processor
        // invocations, otherwise the analysis will produce wrong instruction
        // references.
        final List<MethodTransform> transformers = new ArrayList<>();
        transformers.add(ClassFileCodeTransformer.shiftLocalVarSlotCodeTransformer(-code.getParameterSlotCount()));

        if (options.contains (CodeOption.DYNAMIC_BYPASS) && __snippetDynamicBypass) {
            transformers.add(ClassFileCodeTransformer.insertDynamicBypassControlCodeTransformer());
        }

        if (options.contains (CodeOption.CATCH_EXCEPTIONS)) {
            transformers.add(ClassFileCodeTransformer.InsertExceptionHandlerCodeTransformer(__template.location()));
        }

        MethodModel transformedMethod = ClassFileCodeTransformer.applyTransformers(code.getMethod(), transformers);
        code.setNewMethod(transformedMethod);

        // Analyze code:
        //
        // Find argument processor invocations so that we can determine the
        // complete set of static context methods invoked within the snippet.
        // This is required later to prepare static context data for all snippet
        // invocations.
        //
        // No other modification should be done to the snippet code before
        // weaving, otherwise the produced instruction references will be
        // invalid.
        //
        // TODO LB: Why do we reference the invocations by bytecode index and
        // not an instruction node reference? Possibly because the index will
        // be still valid after cloning the code.
        final Map <Integer, ProcInvocation> argProcInvocations = __collectArgProcInvocations(code.getInstructions(), processor, marker);

        return new SnippetCode(code, argProcInvocations);
    }


    private Map<Integer, ProcInvocation> __collectArgProcInvocations(
            final List<CodeElement> instructions, final Map<ClassDesc, ArgProcessor> processorMap, final Marker marker
    ) throws ProcessorException, ReflectionException {
        final Map <Integer, ProcInvocation> result = new HashMap<>();

        int instructionIndex = 0;
        for (CodeElement codeElement: instructions) {
            final ProcessorInfo apInfo = insnInvokesProcessor(codeElement, instructionIndex, processorMap, marker, instructions);
            if (apInfo != null) {
                result.put(apInfo.insnIndex, apInfo.invocation);
            }
            instructionIndex++;
        }
        return result;
    }


    private static class ProcessorInfo {
        final Integer insnIndex;
        final ProcInvocation invocation;

        public ProcessorInfo (final Integer insnIndex, final ProcInvocation invocation) {
            this.insnIndex = insnIndex;
            this.invocation = invocation;
        }
    }


    private ProcessorInfo insnInvokesProcessor(
            final CodeElement instr, final int i,
            final Map <ClassDesc, ArgProcessor> processors, final Marker marker, final List<CodeElement> allInstructions
    ) throws ProcessorException, ReflectionException {
        // check method invocation
        if (!(instr instanceof InvokeInstruction)) {
            return null;
        }

        // check if the invocation is processor invocation
        final InvokeInstruction min = (InvokeInstruction) instr;
        final ClassDesc apcClass = ClassDesc.ofDescriptor(ArgumentProcessorContext.class.descriptorString());
        if (!apcClass.equals(min.owner().asSymbol())) {
            return null;
        }

        if (!"apply".equals(min.name().stringValue())) {
            return null;
        }

        // resolve load parameter instruction
        final Instruction secondParam = ClassFileHelper.previousRealInstruction(allInstructions, min);
        final Instruction firstParam = ClassFileHelper.previousRealInstruction(allInstructions, secondParam);

        // NOTE: object parameter is ignored - will be removed by weaver

        // the first parameter has to be loaded by LDC
        if (!(firstParam instanceof ConstantInstruction.LoadConstantInstruction)
                || firstParam.opcode() != Opcode.LDC) {
            throw new ProcessorException (
                    "%s: pass the first (class) argument to the apply() method "+
                            "directly as a class literal", __template.location (min)
            );
        }

        // the second parameter has to be loaded by GETSTATIC
        if (!(secondParam instanceof FieldInstruction) || secondParam.opcode() != Opcode.GETSTATIC) {
            throw new ProcessorException (
                    "%s: pass the second (type) argument to the apply() method "+
                            "directly as an enum literal", __template.location (min)
            );
        }

        ConstantInstruction.LoadConstantInstruction constantInstruction = (ConstantInstruction.LoadConstantInstruction) firstParam;
        LoadableConstantEntry constantEntry = constantInstruction.constantEntry();

        // TODO does this lookup actually work????
        Object entry = null;
        try {
            entry = constantEntry.constantValue().resolveConstantDesc(MethodHandles.lookup());
        } catch (ReflectiveOperationException e) {
            throw new ReflectionException(e.getMessage());
        }
        if (!(entry instanceof ClassDesc)) {
            throw new ProcessorException (
                    "%s: unsupported processor type %s",
                    __template.location (min), entry.getClass().toString()
            );
        }

        final ClassDesc processorDesc = (ClassDesc) entry;
        final ArgumentProcessorMode procApplyType = ArgumentProcessorMode.valueOf(
            ((FieldInstruction) secondParam).name().stringValue()
        );


        // if the processor apply type is CALLSITE_ARGS the only allowed marker is BytecodeMarker
        if (ArgumentProcessorMode.CALLSITE_ARGS.equals(procApplyType)
                && marker.getClass() != BytecodeMarker.class) {
            throw new ProcessorException (
                    "%s: ArgumentProcessor applied in the CALLSITE_ARGS mode can "+
                            "be only used with the BytecodeMarker", __template.location (min)
            );
        }

        final ArgProcessor processor = processors.get(processorDesc);
        if (processor == null) {
            throw new ProcessorException (
                    "%s: unknown processor: %s", __template.location (min),
                    processorDesc.displayName()
            );
        }

        // Create an argument processor invocation instance tied to a particular instruction index.
        return new ProcessorInfo(i, new ProcInvocation(processor, procApplyType));
    }

}
