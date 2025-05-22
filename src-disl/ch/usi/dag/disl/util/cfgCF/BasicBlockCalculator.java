package ch.usi.dag.disl.util.cfgCF;

import ch.usi.dag.disl.util.ClassFileHelper;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.*;
import java.util.*;
import java.util.stream.Collectors;

import static ch.usi.dag.disl.util.ClassFileHelper.getLabelTargetMap;

public class BasicBlockCalculator {

    public static List<CodeElement> getAll(final List<CodeElement> instructions,
                                           final List<ExceptionCatch> tryCatchBlocks,
                                           final boolean isPrecise)
    {
        // A holder for instructions that mark the beginning of a basic block.
        // We override the add() method to automatically skip all virtual instructions that are added.
        // We also override the addAll() method to ensure that our modified
        // add() method is used to add the individual elements, because there
        // is no contract in HashSet or Collection to guarantee that.
        Set<CodeElement> bbStarts = new HashSet<>() {
            @Override
            public boolean add(CodeElement element) {
                return super.add(ClassFileHelper.firstNextRealInstruction(instructions, element));
            }

            @Override
            public boolean addAll(Collection<? extends CodeElement> elements) {
                boolean result = false;
                for (final CodeElement element : elements) {
                    final boolean modified = add(element);
                    result = result || modified;
                }
                return result;
            }
        };

        bbStarts.add(instructions.getFirst());  // The first instruction starts a basic block.

        // differently from ASM in the ClassFile API the target of a branch instruction is not the
        // LabelTarget but just the Label, this map is to facilitate the lookup of the actual element
        // in the instructions.
        Map<Label, CodeElement> labelTargetMap = getLabelTargetMap(instructions);

        // Scan all the instructions, identify those that terminate their basic
        // block and collect the starting instructions of the basic blocks that follow them.
        for (final CodeElement element: instructions) {
            switch (element) {
                // For all jump instructions, a basic block starts where the
                // instruction jumps.
                //
                // For conditional jumps or jumps to subroutines, a basic block
                // also starts with the next instruction.
                //
                // The GOTO instruction changes the control flow unconditionally,
                // so only one basic block follows from it.
                case BranchInstruction branch -> {
                    Label target = branch.target();
                    if (labelTargetMap.containsKey(target)) {
                        bbStarts.add(labelTargetMap.get(target));
                    }
                    if (branch.opcode() != Opcode.GOTO) {
                        // There must be a valid (non-virtual) instruction
                        // following a conditional/subroutine jump instruction.
                        Instruction next = ClassFileHelper.nextRealInstruction(instructions, branch);
                        if (next != null) {
                            bbStarts.add(next);
                        }
                    }
                }
                // For the LOOKUPSWITCH and TABLESWITCH instructions, all the
                // targets in the table represent a new basic block, including
                // the default target.
                //
                // Since they are two unrelated classes in ASM, we have to handle
                // each case separately, yet with the same code.
                case LookupSwitchInstruction lookup -> {
                    // TODO remove code duplication
                    Label defaultTarget = lookup.defaultTarget();
                    List<SwitchCase> cases = lookup.cases();
                    List<Label> allLabelsTarget = cases.stream().map(SwitchCase::target).collect(Collectors.toList());
                    allLabelsTarget.add(defaultTarget);
                    List<CodeElement> actualTargets = allLabelsTarget.stream()
                            .filter(labelTargetMap::containsKey)
                            .map(labelTargetMap::get)
                            .toList();
                    bbStarts.addAll(actualTargets);
                }
                case TableSwitchInstruction tableSwitch -> {
                    Label defaultTarget = tableSwitch.defaultTarget();
                    List<SwitchCase> cases = tableSwitch.cases();
                    List<Label> allLabelsTarget = cases.stream().map(SwitchCase::target).collect(Collectors.toList());
                    allLabelsTarget.add(defaultTarget);
                    List<CodeElement> actualTargets = allLabelsTarget.stream()
                            .filter(labelTargetMap::containsKey)
                            .map(labelTargetMap::get)
                            .toList();
                    bbStarts.addAll(actualTargets);
                }
                default -> {}
            }
            // In case of precise basic block marking, any instruction that
            // might throw an exception is potentially the last instruction of
            // a basic block, with the next instruction the beginning of the
            // next basic block.
            if (isPrecise && ClassFileHelper.mightThrowException(element)) {
                bbStarts.add(ClassFileHelper.nextInstruction(instructions, element));
            }
        }

        // All exception handlers start a basic block as well.
        for (final ExceptionCatch exceptionCatch: tryCatchBlocks) {
            Label labelHandler = exceptionCatch.handler();
            if (labelTargetMap.containsKey(labelHandler)) {
                bbStarts.add(labelTargetMap.get(labelHandler));
            }
        }

        // Sort the basic block starting instructions. A LinkedHashSet would
        // not help here, because we were adding entries out-of-order (jumps).
        List<CodeElement> result = new ArrayList<>();
        for (final CodeElement element: instructions ) {
            if (bbStarts.contains(element)) {
                result.add(element);
            }
        }
        return result;
    }
}
