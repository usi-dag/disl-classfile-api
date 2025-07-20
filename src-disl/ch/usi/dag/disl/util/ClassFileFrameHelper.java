package ch.usi.dag.disl.util;

import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.util.ClassFileAnalyzer.*;

import java.lang.classfile.*;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassFileFrameHelper {

    public static Analyzer<BasicValue> getBasicAnalyzer() {
        return new Analyzer<>(new BasicInterpreter());
    }

    public static int getOffset(Frame<BasicValue> frame) {
        int offset = 0;

        for (int i = frame.getStackSize() - 1; i >= 0; i--) {
            BasicValue value = frame.getStack(i);
            offset += value.getSize();
        }
        return offset;
    }

    // generate an instruction list to back up the stack
    public static List<StoreInstruction> enter(Frame<BasicValue> frame, int offset) {
        List<StoreInstruction> storeList = new ArrayList<>();
        for (int i = frame.getStackSize() -1; i >= 0; i--) {
            BasicValue value = frame.getStack(i);
            storeList.add(
                    StoreInstruction.of(value.getTypeKind(), offset)
            );
            offset += value.getSize();
        }
        return storeList;
    }

    // generate an instruction list to restore the stack
    public static List<LoadInstruction> exit(Frame<BasicValue> frame, int offset) {
        List<LoadInstruction> loadList = new ArrayList<>();
        for (int i = frame.getStackSize() -1; i >= 0; i--) {
            BasicValue value = frame.getStack(i);
            loadList.addFirst(
                    LoadInstruction.of(value.getTypeKind(), offset)
            );
            offset += value.getSize();
        }
        return loadList;
    }

    public static Analyzer<SourceValue> getSourceAnalyzer() {
        return new Analyzer<SourceValue>(new SourceInterpreter());
    }

    public static <T extends Value> T getStack(Frame<T> frame, int depth) {
        int index = 0;
        while (depth > 0) {

            depth -= frame.getStack(frame.getStackSize() - 1 - index).getSize();
            index++;
        }
        return frame.getStack(frame.getStackSize() - 1 - index);
    }

    public static <T extends Value> T getStackByIndex(Frame<T> frame, int index) {
        return frame.getStack(frame.getStackSize() - 1 - index);
    }

    public static int dupStack(Frame<SourceValue> frame,
                               int operand, ClassDesc type, int slot, List<CodeElement> methodInstructions) {
        SourceValue source = getStackByIndex(frame, operand);
        for (final CodeElement codeElement: source.instructions) {
            // if the instruction duplicates two-size operand(s), weaver should
            // be careful that the operand might be either 2 one-size operands,
            // or 1 two-size operand.
            if (codeElement instanceof Instruction instruction) {
                switch (instruction.opcode()) {
                    case DUP2 -> {
                        if (source.size != 1) {
                            break;
                        }
                        dupStack(frame, operand + 2, type, slot, methodInstructions);
                    }
                    case DUP2_X1 -> {
                        if (source.size != 1) {
                            break;
                        }

                        dupStack (frame, operand + 3, type, slot, methodInstructions);
                    }
                    case DUP2_X2 -> {
                        if (source.size != 1) {
                            break;
                        }
                        SourceValue x2 = getStackByIndex (frame, operand + 2);
                        dupStack (frame, operand + (4 - x2.size), type, slot, methodInstructions);
                    }
                    case SWAP -> {
                        if (operand > 0 &&
                                getStackByIndex(frame, operand -1).instructions.contains(instruction)) {
                            ClassFileHelper.insertBefore(
                                    instruction,
                                    StackInstruction.of(Opcode.DUP),
                                    methodInstructions);
                            ClassFileHelper.insertBefore(
                                    instruction,
                                    ClassFileHelper.storeVar(TypeKind.from(type), slot),
                                    methodInstructions);
                        }
                    }
                    // TODO what about DUP_X1 and DUP_X2
                    default -> {
                        // Do nothing
                    }
                }
                // insert 'dup' instruction and then store to a local slot
                ClassFileHelper.insert(instruction, ClassFileHelper.storeVar(TypeKind.from(type), slot), methodInstructions);
                ClassFileHelper.insert(
                        instruction,
                        StackInstruction.of(source.size == 2? Opcode.DUP2: Opcode.DUP),
                        methodInstructions
                );
            }
        }
        return source.size;
    }

    public static <V extends Value> Frame<V>[] getFrames(Analyzer<V> analyzer, ClassDesc owner, MethodModelCopy methodModel) {
        try {
            analyzer.analyze(owner, methodModel);
        } catch (AnalyzerException e) {
            throw new DiSLFatalException("Cause by AnalyzerException : \n"
                    + e.getMessage());
        }
        return analyzer.getFrames();
    }

    public static <V extends Value> Frame<V>[] getFrames(Analyzer<V> analyzer, ClassDesc owner,
                                                         List<CodeElement> instructions,
                                                         List<ExceptionCatch> exceptionCatches,
                                                         AccessFlags accessFlags, int maxLocals,
                                                         int maxStack, MethodTypeDesc methodTypeDesc) {
        try {
            analyzer.analyze(
                    owner,
                    instructions,
                    exceptionCatches,
                    accessFlags,
                    maxLocals,
                    maxStack,
                    methodTypeDesc
                    );
        } catch (AnalyzerException e) {
            throw new DiSLFatalException("Cause by AnalyzerException : \n"
                    + e.getMessage() + "\n Owner: " + owner.toString() + " method desc: " + methodTypeDesc);
        }
        return analyzer.getFrames();
    }

    public static <V extends Value> Map<CodeElement, Frame<V>> createMapping(
            Analyzer<V> analyzer, ClassDesc owner, MethodModelCopy methodModel
    ) {
        Map<CodeElement, Frame<V>> mapping = new HashMap<>();

        Frame<V>[] frames = getFrames(analyzer, owner, methodModel);
        List<CodeElement> instructions = methodModel.instructions;
        for (int i = 0; i < frames.length; i++) {
            mapping.put(instructions.get(i), frames[i]);
        }
        return mapping;
    }

    public static <V extends Value> Map<CodeElement, Frame<V>> createMapping(
            Analyzer<V> analyzer, ClassDesc owner, List<CodeElement> instructions,
            List<ExceptionCatch> exceptionCatches, MethodTypeDesc methodDescriptor, AccessFlags flags
    ) {
        int maxStack = ClassFileHelper.getMaxStack(instructions, exceptionCatches);
        int maxLocals = ClassFileHelper.getMaxLocals(instructions, methodDescriptor, flags);
        Map<CodeElement, Frame<V>> mapping = new HashMap<>();
        Frame<V>[] frames = getFrames(analyzer, owner, instructions, exceptionCatches, flags, maxLocals, maxStack, methodDescriptor);

        for (int i = 0; i < frames.length; i++) {
            mapping.put(instructions.get(i), frames[i]);
        }
        return mapping;
    }

    public static Map<CodeElement, Frame<BasicValue>> createBasicMapping(
            ClassDesc owner, MethodModelCopy method, List<CodeElement> instructions) {
        return createMapping(getBasicAnalyzer(), owner, instructions, method.exceptionHandlers(), method.methodTypeSymbol(), method.flags());
    }

    public static Map<CodeElement, Frame<SourceValue>> createSourceMapping(
            ClassDesc owner, MethodModelCopy method, List<CodeElement> instructions) {
        return createMapping(getSourceAnalyzer(), owner, instructions, method.exceptionHandlers(), method.methodTypeSymbol(), method.flags());
    }

}
