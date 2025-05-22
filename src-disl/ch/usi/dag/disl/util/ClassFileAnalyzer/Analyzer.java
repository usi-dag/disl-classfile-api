package ch.usi.dag.disl.util.ClassFileAnalyzer;
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.


// this code was ported from ASM, but is modified to work with the Java CLass File API
import ch.usi.dag.disl.CustomCodeElements.FutureLabelTarget;
import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;
import java.util.stream.Collectors;

public class Analyzer<V extends Value> {

    /** The interpreter to use to symbolically interpret the bytecode instructions. */
    private final Interpreter<V> interpreter;

    /** The instructions of the currently analyzed method. */
    private List<CodeElement> instructions;

    private int instructionListSize;

    /** The exception handlers of the currently analyzed method (one list per instruction index). */
    private List<ExceptionCatch>[] handlers;

    /** The execution stack frames of the currently analyzed method (one per instruction index). */
    private Frame<V>[] frames;

    /** The subroutines of the currently analyzed method (one per instruction index). */
    private Subroutine[] subroutines;

    /** The instructions that remain to process (one boolean per instruction index). */
    private boolean[] inInstructionsToProcess;

    /** The indices of the instructions that remain to process in the currently analyzed method. */
    private int[] instructionsToProcess;

    /** The number of instructions that remain to process in the currently analyzed method. */
    private int numInstructionsToProcess;

    /** this map is to have a faster search */
    private Map<Label, CodeElement> labelTargetMap;

    private int maxLocals;
    private int maxStack;

    /**
     * Constructs a new {@link Analyzer}.
     *
     * @param interpreter the interpreter to use to symbolically interpret the bytecode instructions.
     */
    public Analyzer(final Interpreter<V> interpreter) {
        this.interpreter = interpreter;
    }


    /**
     * Analyzes the given method.
     *
     * @param owner the internal name of the class to which 'method' belongs
     *              like "java/lang/String"
     *              (see {@link ClassDesc#packageName()} + "/" +  {@link ClassDesc#displayName()}).
     * @param method the method to be analyzed. The maxStack and maxLocals fields must have correct
     *     values.
     * @return the symbolic state of the execution stack frame at each bytecode instruction of the
     *     method. The size of the returned array is equal to the number of instructions (and labels)
     *     of the method. A given frame is {@literal null} if and only if the corresponding
     *     instruction cannot be reached (dead code).
     * @throws AnalyzerException if a problem occurs during the analysis.
     */
    @SuppressWarnings("unchecked")
    public Frame<V>[] analyze(final ClassDesc owner, final MethodModelCopy method) throws AnalyzerException {
        if (method.flags().has(AccessFlag.NATIVE) || method.flags().has(AccessFlag.ABSTRACT) ||
                !method.hasCode()
        ) {
            frames = (Frame<V>[]) new Frame<?>[0];
            return frames;
        }
        int maxLocals = method.maxLocals();
        int maxStack = method.maxStack();


        return analyze(
                owner,
                method.instructions(),
                method.exceptionHandlers(),
                method.flags(),
                maxLocals,
                maxStack,
                method.methodTypeSymbol()
        );
    }

    @SuppressWarnings("unchecked")
    public Frame<V>[] analyze(final ClassDesc owner,
                              final List<CodeElement> instructions,
                              List<ExceptionCatch> exceptionCatchList,
                              AccessFlags accessFlags,
                              int maxLocals,
                              int maxStack,
                              MethodTypeDesc methodDescriptor
    ) throws AnalyzerException {

        if (accessFlags.has(AccessFlag.NATIVE) ||accessFlags.has(AccessFlag.ABSTRACT) ||
                instructions.isEmpty()
        ) {
            frames = (Frame<V>[]) new Frame<?>[0];
            return frames;
        }

        this.instructions = instructions;
        this.instructionListSize = instructions.size();
        this.handlers = (List<ExceptionCatch>[]) new List<?>[instructionListSize];
        this.frames = (Frame<V>[]) new Frame<?>[instructionListSize];
        this.subroutines = new Subroutine[instructionListSize];
        this.inInstructionsToProcess = new boolean[instructionListSize];
        this.instructionsToProcess = new int[instructionListSize];
        this.numInstructionsToProcess = 0;
        this.labelTargetMap = ClassFileHelper.getLabelTargetMap(instructions);
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;

        // For each exception handler, and each instruction within its range, record in 'handlers' the
        // fact that execution can flow from this instruction to the exception handler.
        for (int i = 0; i < exceptionCatchList.size(); ++i) {
            ExceptionCatch exceptionCatch = exceptionCatchList.get(i);
            Label startLabel = exceptionCatch.tryStart();
            Label endLabel = exceptionCatch.tryEnd();
            int startIndex = instructions.indexOf(labelTargetMap.get(startLabel));
            int endIndex = instructions.indexOf(labelTargetMap.get(endLabel));
            for (int j = startIndex; j < endIndex; ++j) {
                List<ExceptionCatch> instructionHandlers = handlers[j];
                if (instructionHandlers == null) {
                    instructionHandlers = new ArrayList<>();
                    handlers[j] = instructionHandlers;
                }
                instructionHandlers.add(exceptionCatch);
            }
        }

        // Finds the method's subroutines.
        findSubroutines(maxLocals);

        // Initializes the data structures for the control flow analysis.
        Frame<V> currentFrame;
        try {
            currentFrame = computeInitialFrame(owner, accessFlags, methodDescriptor);
            merge(0, currentFrame, null);
        } catch (RuntimeException e) {
            throw new AnalyzerException(instructions.getFirst(), "Error at instruction 0: " + e.getMessage(), e);
        }

        // Control flow analysis.
        while (numInstructionsToProcess > 0) {
            // Get and remove one instruction from the list of instructions to process.
            int instructionIndex = instructionsToProcess[--numInstructionsToProcess];
            Frame<V> oldFrame = frames[instructionIndex];
            Subroutine subroutine = subroutines[instructionIndex];
            inInstructionsToProcess[instructionIndex] = false;

            // Simulate the execution of this instruction.
            CodeElement codeElement = null;
            try {
                codeElement = instructions.get(instructionIndex);
                if (codeElement instanceof PseudoInstruction || codeElement instanceof FutureLabelTarget) {
                    // FutureLabelTarget extend CustomAttribute but has the same meaning as LabelTarget which is a PseudoInstruction
                    currentFrame.init(oldFrame);
                    merge(instructionIndex + 1, oldFrame, subroutine);
                    newControlFlowEdge(instructionIndex, instructionIndex + 1);
                } else if (codeElement instanceof Instruction instruction) {
                    currentFrame.init(oldFrame).execute(instruction, interpreter);
                    subroutine = subroutine == null? null : new Subroutine(subroutine);
                    
                    if (codeElement instanceof BranchInstruction branchInstruction) {
                        Opcode instrOpcode = branchInstruction.opcode();
                        if (instrOpcode != Opcode.GOTO
                                && instrOpcode != Opcode.GOTO_W
                                && instrOpcode != Opcode.JSR
                                && instrOpcode != Opcode.JSR_W
                        ) {
                            currentFrame.initJumpTarget(instrOpcode, null);
                            merge(instructionIndex + 1, currentFrame, subroutine);
                            newControlFlowEdge(instructionIndex, instructionIndex + 1);
                        }
                        int jumpInstrIndex = instructions.indexOf(labelTargetMap.get(branchInstruction.target()));
                        currentFrame.initJumpTarget(instrOpcode, branchInstruction.target());
                        if (instrOpcode == Opcode.JSR || instrOpcode == Opcode.JSR_W) {
                            merge(
                                    jumpInstrIndex,
                                    currentFrame,
                                    new Subroutine(branchInstruction.target(), maxLocals, branchInstruction)
                            );
                        } else {
                            merge(jumpInstrIndex, currentFrame, subroutine);
                        }
                        newControlFlowEdge(instructionIndex, jumpInstrIndex);
                    } else if (codeElement instanceof LookupSwitchInstruction lsi) {
                        int targetInstrIndex = instructions.indexOf(labelTargetMap.get(lsi.defaultTarget()));
                        currentFrame.initJumpTarget(lsi.opcode(), lsi.defaultTarget());
                        merge(targetInstrIndex, currentFrame, subroutine);
                        newControlFlowEdge(instructionIndex, targetInstrIndex);
                        for (SwitchCase switchCase: lsi.cases()) {
                            Label label = switchCase.target();
                            targetInstrIndex = instructions.indexOf(labelTargetMap.get(label));
                            currentFrame.initJumpTarget(lsi.opcode(), label);
                            merge(targetInstrIndex, currentFrame, subroutine);
                            newControlFlowEdge(instructionIndex, targetInstrIndex);
                        }
                    } else if (codeElement instanceof TableSwitchInstruction tsi) {
                        int targetInstrIndex = instructions.indexOf(labelTargetMap.get(tsi.defaultTarget()));
                        currentFrame.initJumpTarget(tsi.opcode(), tsi.defaultTarget());
                        merge(targetInstrIndex, currentFrame, subroutine);
                        newControlFlowEdge(instructionIndex, targetInstrIndex);
                        for (SwitchCase switchCase: tsi.cases()) {
                            Label label = switchCase.target();
                            currentFrame.initJumpTarget(tsi.opcode(), label);
                            targetInstrIndex = instructions.indexOf(labelTargetMap.get(label));
                            merge(targetInstrIndex, currentFrame, subroutine);
                            newControlFlowEdge(instructionIndex, targetInstrIndex);
                        }
                    } else if (codeElement instanceof DiscontinuedInstruction.RetInstruction ret) {
                        if (subroutine == null) {
                            throw new AnalyzerException(ret, "RET instruction outside of a subroutine");
                        }
                        for (int i = 0; i < subroutine.callers.size(); ++i) {
                            Instruction caller = subroutine.callers.get(i);
                            int jsrIndex = instructions.indexOf(caller);
                            if (frames[jsrIndex] != null) {
                                merge(
                                        jsrIndex + 1,
                                        frames[jsrIndex],
                                        currentFrame,
                                        subroutines[jsrIndex],
                                        subroutine.localsUsed
                                );
                                newControlFlowEdge(instructionIndex, jsrIndex + 1);
                            }
                        }
                    } else if (!(codeElement instanceof ReturnInstruction) && !(codeElement instanceof ThrowInstruction)) {
                        if (subroutine != null) {
                            switch (codeElement) {
                                case LoadInstruction loadInstruction -> {
                                    int varIndex = loadInstruction.slot();
                                    subroutine.localsUsed[varIndex] = true;
                                    if (loadInstruction.opcode() == Opcode.LLOAD
                                            || loadInstruction.opcode() == Opcode.LLOAD_0
                                            || loadInstruction.opcode() == Opcode.LLOAD_1
                                            || loadInstruction.opcode() == Opcode.LLOAD_2
                                            || loadInstruction.opcode() == Opcode.LLOAD_3
                                            || loadInstruction.opcode() == Opcode.LLOAD_W
                                            || loadInstruction.opcode() == Opcode.DLOAD
                                            || loadInstruction.opcode() == Opcode.DLOAD_0
                                            || loadInstruction.opcode() == Opcode.DLOAD_1
                                            || loadInstruction.opcode() == Opcode.DLOAD_2
                                            || loadInstruction.opcode() == Opcode.DLOAD_3
                                            || loadInstruction.opcode() == Opcode.DLOAD_W
                                    ) {
                                        subroutine.localsUsed[varIndex + 1] = true;
                                    }
                                }
                                case StoreInstruction storeInstruction -> {
                                    int varIndex = storeInstruction.slot();
                                    subroutine.localsUsed[varIndex] = true;
                                    if (storeInstruction.opcode() == Opcode.LSTORE
                                            || storeInstruction.opcode() == Opcode.LSTORE_0
                                            || storeInstruction.opcode() == Opcode.LSTORE_1
                                            || storeInstruction.opcode() == Opcode.LSTORE_2
                                            || storeInstruction.opcode() == Opcode.LSTORE_3
                                            || storeInstruction.opcode() == Opcode.LSTORE_W
                                            || storeInstruction.opcode() == Opcode.DSTORE
                                            || storeInstruction.opcode() == Opcode.DSTORE_0
                                            || storeInstruction.opcode() == Opcode.DSTORE_1
                                            || storeInstruction.opcode() == Opcode.DSTORE_2
                                            || storeInstruction.opcode() == Opcode.DSTORE_3
                                            || storeInstruction.opcode() == Opcode.DSTORE_W
                                    ) {
                                        subroutine.localsUsed[varIndex + 1] = true;
                                    }
                                }
                                case IncrementInstruction incInstr -> {
                                    int varIndex = incInstr.slot();
                                    subroutine.localsUsed[varIndex] = true;
                                }
                                default -> {
                                }
                            }
                        }
                        merge(instructionIndex + 1, currentFrame, subroutine);
                        newControlFlowEdge(instructionIndex, instructionIndex + 1);
                    }
                }

                List<ExceptionCatch> instrHandlers = handlers[instructionIndex];
                if (instrHandlers != null) {
                    for (ExceptionCatch exceptionCatch: instrHandlers) {
                        Optional<ClassEntry> optionalClassEntry = exceptionCatch.catchType();
                        TypeKind typeKind;
                        if (optionalClassEntry.isEmpty()) {
                            typeKind = TypeKind.from(Throwable.class);
                        } else {
                            typeKind = optionalClassEntry.get().typeKind();
                        }
                        if (newControlFlowExceptionEdge(instructionIndex, exceptionCatch)) {
                            // Merge the frame *before* this instruction, with its stack cleared and an exception
                            // pushed, with the handler's frame.
                            Frame<V> handler = newFrame(oldFrame);
                            handler.clearStack();
                            V exceptionValue = interpreter.newExceptionValue(exceptionCatch, handler, typeKind);
                            handler.push(exceptionValue);
                            merge(
                                    instructions.indexOf(labelTargetMap.get(exceptionCatch.handler())),
                                    handler,
                                    subroutine
                            );
                            // Merge the frame *after* this instruction, with its stack cleared and an exception
                            // pushed, with the handler's frame.
                            handler = newFrame(currentFrame);
                            handler.clearStack();
                            handler.push(exceptionValue);
                            merge(
                                    instructions.indexOf(labelTargetMap.get(exceptionCatch.handler())),
                                    handler,
                                    subroutine
                            );
                        }
                    }
                }

            } catch (AnalyzerException e) {
                throw new AnalyzerException(
                        e.node, "Error at instruction " + instructionIndex + ": " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new AnalyzerException(
                        codeElement, "Error at instruction " + instructionIndex + ": " + e.getMessage(), e);
            }
        }

        return frames;
    }

    /**
     * Computes and returns the maximum stack size of a method, given its stack map frames.
     *
     * @param frames the stack map frames of a method.
     * @return the maximum stack size of the given method.
     */
    private static int computeMaxStack(final Frame<?>[] frames) {
        int maxStack = 0;
        for (Frame<?> frame : frames) {
            if (frame != null) {
                int stackSize = 0;
                for (int i = 0; i < frame.getStackSize(); ++i) {
                    stackSize += frame.getStack(i).getSize();
                }
                maxStack = Math.max(maxStack, stackSize);
            }
        }
        return maxStack;
    }



    /**
     * Finds the subroutines of the currently analyzed method and stores them in {@link #subroutines}.
     *
     * @param maxLocals the maximum number of local variables of the currently analyzed method (long
     *     and double values count for two variables).
     * @throws AnalyzerException if the control flow graph can fall off the end of the code.
     */
    private void findSubroutines(final int maxLocals) throws AnalyzerException {
        // For each instruction, compute the subroutine to which it belongs.
        // Follow the main 'subroutine', and collect the jsr instructions to nested subroutines.
        Subroutine main = new Subroutine(null, maxLocals, null);
        List<DiscontinuedInstruction.JsrInstruction> jsrInstructions = new ArrayList<>();
        findSubroutine(0, main, jsrInstructions);
        // Follow the nested subroutines, and collect their own nested subroutines, until all
        // subroutines are found.
        Map<Label, Subroutine> jsrSubroutine = new HashMap<>();
        while (!jsrInstructions.isEmpty()) {
            DiscontinuedInstruction.JsrInstruction jsrInstruction = jsrInstructions.removeFirst();
            Subroutine subroutine = jsrSubroutine.get(jsrInstruction.target());
            if (subroutine == null) {
                subroutine = new Subroutine(jsrInstruction.target(), maxLocals, jsrInstruction);
                jsrSubroutine.put(jsrInstruction.target(), subroutine);
                findSubroutine(
                        instructions.indexOf(labelTargetMap.get(jsrInstruction.target())),
                        subroutine,
                        jsrInstructions);
            } else {
                subroutine.callers.add(jsrInstruction);
            }
        }

        // Clear the main 'subroutine', which is not a real subroutine (and was used only as an
        // intermediate step above to find the real ones).
        for (int i = 0; i < instructionListSize; ++i) {
            if (subroutines[i] != null && subroutines[i].start == null) {
                subroutines[i] = null;
            }
        }
    }


    /**
     * Follows the control flow graph of the currently analyzed method, starting at the given
     * instruction index, and stores a copy of the given subroutine in {@link #subroutines} for each
     * encountered instruction. Jumps to nested subroutines are <i>not</i> followed: instead, the
     * corresponding instructions are put in the given list.
     *
     * @param instructionIndex an instruction index.
     * @param subroutine a subroutine.
     * @param jsrInstructions where the jsr instructions for nested subroutines must be put.
     *                        note that it is named jsr because it was in the original code, but it
     *                        includes also other kind of other jump instructions. Most likely a jsr
     *                        instruction will not be used since is deprecated
     * @throws AnalyzerException if the control flow graph can fall off the end of the code.
     */
    private void findSubroutine(final int instructionIndex, final Subroutine subroutine,
                                final List<DiscontinuedInstruction.JsrInstruction> jsrInstructions) throws AnalyzerException {
        ArrayList<Integer> instructionIndicesToProcess = new ArrayList<>();
        instructionIndicesToProcess.add(instructionIndex);
        while (!instructionIndicesToProcess.isEmpty()) {
            int currentInstructionIndex = instructionIndicesToProcess.removeLast();
            if (currentInstructionIndex < 0 || currentInstructionIndex >= instructionListSize) {
                throw new AnalyzerException(null, "Execution can fall off the end of the code");
            }
            if (subroutines[currentInstructionIndex] != null) {
                continue;
            }
            subroutines[currentInstructionIndex] = new Subroutine(subroutine);
            CodeElement currentInstruction = instructions.get(currentInstructionIndex);

            // Push the normal successors of currentInsn onto instructionIndicesToProcess.
            switch (currentInstruction) {
                case DiscontinuedInstruction.JsrInstruction jsr -> {
                    // Do not follow a jsr, it leads to another subroutine!
                    jsrInstructions.add(jsr);
                }
                case BranchInstruction branchInstruction -> {
                    instructionIndicesToProcess.add(
                            instructions.indexOf(labelTargetMap.get(branchInstruction.target()))
                    );
                }
                case TableSwitchInstruction tsi -> {
                    findSubroutine(
                            instructions.indexOf(labelTargetMap.get(tsi.defaultTarget())),
                            subroutine,
                            jsrInstructions
                    );
                    for (SwitchCase switchCase: tsi.cases()) {
                        Label target = switchCase.target();
                        instructionIndicesToProcess.add(
                                instructions.indexOf(labelTargetMap.get(target))
                        );
                    }
                }
                case LookupSwitchInstruction lsi -> {
                    findSubroutine(
                            instructions.indexOf(labelTargetMap.get(lsi.defaultTarget())),
                            subroutine,
                            jsrInstructions
                    );
                    for (SwitchCase switchCase: lsi.cases()) {
                        instructionIndicesToProcess.add(
                                instructions.indexOf(labelTargetMap.get(switchCase.target()))
                        );
                    }
                }
                default -> {}
            }

            // Push the exception handler successors of currentInsn onto instructionIndicesToProcess.
            List<ExceptionCatch> instructionsHandlers = handlers[currentInstructionIndex];
            if (instructionsHandlers != null) {
                for (ExceptionCatch exceptionCatch: instructionsHandlers) {
                    instructionIndicesToProcess.add(
                            instructions.indexOf(labelTargetMap.get(exceptionCatch.handler()))
                    );
                }
            }

            // Push the next instruction, if the control flow can go from currentInstruction to the next.
            if (currentInstruction instanceof Instruction instruction) {
                // only instructions have the opcode, for pseudoInstruction
                // we can push the next
                switch (instruction.opcode()) {
                    case Opcode.GOTO,
                         Opcode.GOTO_W,
                         Opcode.TABLESWITCH,
                         Opcode.LOOKUPSWITCH,
                         Opcode.IRETURN,
                         Opcode.LRETURN,
                         Opcode.FRETURN,
                         Opcode.DRETURN,
                         Opcode.ARETURN,
                         Opcode.RETURN,
                         Opcode.ATHROW:
                            break;
                    default:
                        instructionIndicesToProcess.add(currentInstructionIndex + 1);
                        break;
                }
            } else {
                instructionIndicesToProcess.add(currentInstructionIndex + 1);
            }
        }
    }

    /**
     * Computes the initial execution stack frame of the given method.
     *
     * @param owner the classDesc of the class to which 'method' belongs.
     * @param flags the flags of the method.
     * @param methodDescriptor the descriptor of the method
     * @return the initial execution stack frame of the 'method'.
     */
    private Frame<V> computeInitialFrame(final ClassDesc owner, final AccessFlags flags, MethodTypeDesc methodDescriptor) {
        Frame<V> frame = newFrame(maxLocals, maxStack);
        int currentLocal = 0;
        // TODO are there other flags that must be excluded????
        boolean isInstanceMethod = !flags.has(AccessFlag.STATIC);
        if (isInstanceMethod) {
            frame.setLocal(
                    currentLocal, interpreter.newParameterValue(isInstanceMethod,currentLocal, TypeKind.from(owner))
            );
            currentLocal++;
        }
        ClassDesc[] argumentTypes = methodDescriptor.parameterArray();
        for (ClassDesc argumentType: argumentTypes) {
            frame.setLocal(
                    currentLocal,
                    interpreter.newParameterValue(isInstanceMethod,currentLocal, TypeKind.from(argumentType))
            );
            currentLocal++;
            if (TypeKind.from(argumentType).slotSize() == 2) {
                frame.setLocal(currentLocal, interpreter.newEmptyValue(currentLocal));
                currentLocal++;
            }
        }
        while (currentLocal < maxLocals) {
            frame.setLocal(currentLocal, interpreter.newEmptyValue(currentLocal));
            currentLocal++;
        }
        frame.setReturn(interpreter.newReturnTypeValue(TypeKind.from(methodDescriptor.returnType())));
        return frame;
    }

    /**
     * Returns the symbolic execution stack frame for each instruction of the last analyzed method.
     *
     * @return the symbolic state of the execution stack frame at each bytecode instruction of the
     *     method. The size of the returned array is equal to the number of instructions (and labels)
     *     of the method. A given frame is {@literal null} if the corresponding instruction cannot be
     *     reached, or if an error occurred during the analysis of the method.
     */
    public Frame<V>[] getFrames() {
        return frames;
    }


    /**
     * Returns the exception handlers for the given instruction.
     *
     * @param insnIndex the index of an instruction of the last analyzed method.
     * @return a list of {@link ExceptionCatch} objects.
     */
    public List<ExceptionCatch> getHandlers(final int insnIndex) {
        return handlers[insnIndex];
    }


    /**
     * Merges the given frame and subroutine into the frame and subroutines at the given instruction
     * index. If the frame or the subroutine at the given instruction index changes as a result of
     * this merge, the instruction index is added to the list of instructions to process (if it is not
     * already the case).
     *
     * @param instructionIndex an instruction index.
     * @param frame a frame. This frame is left unchanged by this method.
     * @param subroutine a subroutine. This subroutine is left unchanged by this method.
     * @throws AnalyzerException if the frames have incompatible sizes.
     */
    private void merge(final int instructionIndex, final Frame<V> frame, final Subroutine subroutine)
            throws AnalyzerException {
        boolean changed;
        Frame<V> oldFrame = frames[instructionIndex];
        if (oldFrame == null) {
            frames[instructionIndex] = newFrame(frame);
            changed = true;
        } else {
            changed = oldFrame.merge(frame, interpreter);
        }
        Subroutine oldSubroutine = subroutines[instructionIndex];
        if (oldSubroutine == null) {
            if (subroutine != null) {
                subroutines[instructionIndex] = new Subroutine(subroutine);
                changed = true;
            }
        } else {
            if (subroutine != null) {
                changed |= oldSubroutine.merge(subroutine);
            }
        }
        if (changed && !inInstructionsToProcess[instructionIndex]) {
            inInstructionsToProcess[instructionIndex] = true;
            instructionsToProcess[numInstructionsToProcess++] = instructionIndex;
        }
    }

    /**
     * Merges the given frame and subroutine into the frame and subroutines at the given instruction
     * index (case of a RET instruction). If the frame or the subroutine at the given instruction
     * index changes as a result of this merge, the instruction index is added to the list of
     * instructions to process (if it is not already the case).
     *
     * @param insnIndex the index of an instruction immediately following a jsr instruction.
     * @param frameBeforeJsr the execution stack frame before the jsr instruction. This frame is
     *     merged into 'frameAfterRet'.
     * @param frameAfterRet the execution stack frame after a ret instruction of the subroutine. This
     *     frame is merged into the frame at 'insnIndex' (after it has itself been merge with
     *     'frameBeforeJsr').
     * @param subroutineBeforeJsr if the jsr is itself part of a subroutine (case of nested
     *     subroutine), the subroutine it belongs to.
     * @param localsUsed the local variables read or written in the subroutine.
     * @throws AnalyzerException if the frames have incompatible sizes.
     */
    private void merge(
            final int insnIndex,
            final Frame<V> frameBeforeJsr,
            final Frame<V> frameAfterRet,
            final Subroutine subroutineBeforeJsr,
            final boolean[] localsUsed)
            throws AnalyzerException {
        frameAfterRet.merge(frameBeforeJsr, localsUsed);

        boolean changed;
        Frame<V> oldFrame = frames[insnIndex];
        if (oldFrame == null) {
            frames[insnIndex] = newFrame(frameAfterRet);
            changed = true;
        } else {
            changed = oldFrame.merge(frameAfterRet, interpreter);
        }
        Subroutine oldSubroutine = subroutines[insnIndex];
        if (oldSubroutine != null && subroutineBeforeJsr != null) {
            changed |= oldSubroutine.merge(subroutineBeforeJsr);
        }
        if (changed && !inInstructionsToProcess[insnIndex]) {
            inInstructionsToProcess[insnIndex] = true;
            instructionsToProcess[numInstructionsToProcess++] = insnIndex;
        }
    }

    /**
     * Constructs a new frame with the given size.
     *
     * @param numLocals the maximum number of local variables of the frame.
     * @param numStack the maximum stack size of the frame.
     * @return the created frame.
     */
    protected Frame<V> newFrame(final int numLocals, final int numStack) {
        return new Frame<>(numLocals, numStack);
    }

    /**
     * Constructs a copy of the given frame.
     *
     * @param frame a frame.
     * @return the created frame.
     */
    protected Frame<V> newFrame(final Frame<? extends V> frame) {
        return new Frame<>(frame);
    }

    /**
     * Creates a control flow graph edge. The default implementation of this method does nothing. It
     * can be overridden in order to construct the control flow graph of a method (this method is
     * called by the {@link #analyze} method during its visit of the method's code).
     *
     * @param insnIndex an instruction index.
     * @param successorIndex index of a successor instruction.
     */
    protected void newControlFlowEdge(final int insnIndex, final int successorIndex) {
        // Nothing to do.
    }


    /**
     * Creates a control flow graph edge corresponding to an exception handler. The default
     * implementation of this method does nothing. It can be overridden in order to construct the
     * control flow graph of a method (this method is called by the {@link #analyze} method during its
     * visit of the method's code).
     *
     * @param insnIndex an instruction index.
     * @param successorIndex index of a successor instruction.
     * @return true if this edge must be considered in the data flow analysis performed by this
     *     analyzer, or false otherwise. The default implementation of this method always returns
     *     true.
     */
    protected boolean newControlFlowExceptionEdge(final int insnIndex, final int successorIndex) {
        return true;
    }

    /**
     * Creates a control flow graph edge corresponding to an exception handler. The default
     * implementation of this method delegates to {@link #newControlFlowExceptionEdge(int, int)}. It
     * can be overridden in order to construct the control flow graph of a method (this method is
     * called by the {@link #analyze} method during its visit of the method's code).
     *
     * @param insnIndex an instruction index.
     * @param tryCatchBlock TryCatchBlockNode corresponding to this edge.
     * @return true if this edge must be considered in the data flow analysis performed by this
     *     analyzer, or false otherwise. The default implementation of this method delegates to {@link
     *     #newControlFlowExceptionEdge(int, int)}.
     */
    protected boolean newControlFlowExceptionEdge(
            final int insnIndex, final ExceptionCatch tryCatchBlock) {
        return newControlFlowExceptionEdge(insnIndex, instructions.indexOf(labelTargetMap.get(tryCatchBlock.handler())));
    }

}
