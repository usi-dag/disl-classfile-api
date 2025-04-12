package ch.usi.dag.disl.util;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.*;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * I discovered that in some case a list of instructions obtained by CodeModel.elementList can contain multiple
 * references to the same object instruction.
 * Example:
 * If a method contain multiple references to "this", then they are all represented with the same LoadInstruction,
 * all the reference to "this" in the code will be represented with the same object. This causes multiple problems,
 * like when we want to get the next or previous element of the LoadInstruction.
 * The idea is that if we copy the instructions then all the load will be represented by different objects.
 * Since I am not sure which Instruction behave like the Load I made functions to copy any possible instructions.
 * I did not took into consideration the PseudoInstructions, since LabelTarget, LineNumber and the other should
 * all be unique and never repeat in a list (you cant have two line with the same number or two target that point to the same place)
 */
public class ClassFileInstructionClone {

    /**
     * return a new List with all element being a copy of the corresponding element of the original list
     * @param instructions list of instructions
     * @param immutable if the returned list should be immutable or not
     * @return a list with all element copied
     */
    public static List<CodeElement> copyList(List<CodeElement> instructions, boolean immutable) {
        Collector<CodeElement, ?, List<CodeElement>> collector = immutable? Collectors.toUnmodifiableList() : Collectors.toList();
        return instructions.stream().map(c -> {
            if (c instanceof Instruction instruction) {
                return cloneInstruction(instruction);
            }
            return c;
        }).collect(collector);
    }

    /**
     * replace all the Instructions with a copy, do not affect the PseudoInstructions
     * @param instructions list of instructions
     */
    public static void replaceElementWithCopy(List<CodeElement> instructions) {
        for (int index =0; index < instructions.size(); index++) {
            CodeElement element = instructions.get(index);
            if (element instanceof Instruction instruction) {
                instructions.set(index, cloneInstruction(instruction));
            }
        }
    }

    /**
     * Copy an instruction, since the classFile do not implement the .clone() method
     * @param instruction the instruction to copy
     * @return a new instruction with all components equal to the input
     */
    public static Instruction cloneInstruction(Instruction instruction) {
        switch (instruction) {
            case ArrayLoadInstruction arrayLoadInstruction -> {
                return ArrayLoadInstruction.of(arrayLoadInstruction.opcode());
            }
            case ArrayStoreInstruction arrayStoreInstruction -> {
                return ArrayStoreInstruction.of(arrayStoreInstruction.opcode());
            }
            case BranchInstruction branchInstruction -> {
                return BranchInstruction.of(branchInstruction.opcode(), branchInstruction.target());
            }
            case ConstantInstruction.IntrinsicConstantInstruction intrinsicConstantInstruction -> {
                return ConstantInstruction.ofIntrinsic(intrinsicConstantInstruction.opcode());
            }
            case ConstantInstruction.ArgumentConstantInstruction argumentConstantInstruction -> {
                return ConstantInstruction.ofArgument(argumentConstantInstruction.opcode(), argumentConstantInstruction.constantValue());
            }
            case ConstantInstruction.LoadConstantInstruction loadConstantInstruction -> {
                return ConstantInstruction.ofLoad(loadConstantInstruction.opcode(), loadConstantInstruction.constantEntry());
            }
            case ConvertInstruction convertInstruction -> {
                return ConvertInstruction.of(convertInstruction.opcode());
            }
            case DiscontinuedInstruction.JsrInstruction jsrInstruction -> {
                return DiscontinuedInstruction.JsrInstruction.of(jsrInstruction.target());
            }
            case DiscontinuedInstruction.RetInstruction retInstruction -> {
                return DiscontinuedInstruction.RetInstruction.of(retInstruction.slot());
            }
            case FieldInstruction fieldInstruction -> {
                return FieldInstruction.of(fieldInstruction.opcode(), fieldInstruction.field());
            }
            case InvokeDynamicInstruction invokeDynamicInstruction -> {
                return InvokeDynamicInstruction.of(invokeDynamicInstruction.invokedynamic());
            }
            case InvokeInstruction invokeInstruction -> {
                return InvokeInstruction.of(invokeInstruction.opcode(), invokeInstruction.method());
            }
            case LoadInstruction loadInstruction -> {
                return LoadInstruction.of(loadInstruction.opcode(), loadInstruction.slot());
            }
            case StoreInstruction storeInstruction -> {
                return StoreInstruction.of(storeInstruction.opcode(), storeInstruction.slot());
            }
            case IncrementInstruction incrementInstruction -> {
                return IncrementInstruction.of(incrementInstruction.slot(), incrementInstruction.constant());
            }
            case LookupSwitchInstruction lookupSwitchInstruction -> {
                return LookupSwitchInstruction.of(lookupSwitchInstruction.defaultTarget(), lookupSwitchInstruction.cases());
            }
            case MonitorInstruction monitorInstruction -> {
                return MonitorInstruction.of(monitorInstruction.opcode());
            }
            case NewMultiArrayInstruction newMultiArrayInstruction -> {
                return NewMultiArrayInstruction.of(newMultiArrayInstruction.arrayType(), newMultiArrayInstruction.dimensions());
            }
            case NewObjectInstruction newObjectInstruction -> {
                return NewObjectInstruction.of(newObjectInstruction.className());
            }
            case NewPrimitiveArrayInstruction newPrimitiveArrayInstruction -> {
                return NewPrimitiveArrayInstruction.of(newPrimitiveArrayInstruction.typeKind());
            }
            case NewReferenceArrayInstruction newReferenceArrayInstruction -> {
                return NewReferenceArrayInstruction.of(newReferenceArrayInstruction.componentType());
            }
            case NopInstruction _ -> {
                return NopInstruction.of();
            }
            case OperatorInstruction operatorInstruction -> {
                return OperatorInstruction.of(operatorInstruction.opcode());
            }
            case ReturnInstruction returnInstruction -> {
                return ReturnInstruction.of(returnInstruction.typeKind());
            }
            case StackInstruction stackInstruction -> {
                return StackInstruction.of(stackInstruction.opcode());
            }
            case TableSwitchInstruction tableSwitchInstruction -> {
                return TableSwitchInstruction.of(tableSwitchInstruction.lowValue(), tableSwitchInstruction.highValue(), tableSwitchInstruction.defaultTarget(), tableSwitchInstruction.cases());
            }
            case ThrowInstruction _ -> {
                return ThrowInstruction.of();
            }
            case TypeCheckInstruction typeCheckInstruction -> {
                return TypeCheckInstruction.of(typeCheckInstruction.opcode(), typeCheckInstruction.type());
            }
            default -> throw new RuntimeException("Error while copying instruction: " + instruction);
        }
    }
}
