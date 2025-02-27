package ch.usi.dag.disl.coderep;

import java.lang.classfile.*;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.util.List;

public class ClassFileCodeTransformer {
    public static MethodModel replaceReturnsWithGoto(final MethodModel oldMethod) {
        // TODO this method create a new class with the method edited and then return only the new method, there might be
        //  a better way to apply transformations, but in the ClassFile all element are immutable and to create new one you might need a
        //  builder. In this case it is essential to generate a new label.

        if (oldMethod.code().isEmpty()) {
            return oldMethod;  // Do nothing if there is no code
        }
        final List<CodeElement> oldInstructions = oldMethod.code().get().elementList();

        final List<CodeElement> returnInstructions = oldInstructions.stream()
                .filter(i -> i instanceof ReturnInstruction).toList();

        if (!returnInstructions.isEmpty()) { // if there is one or more return

            byte[] transformedClass = ClassFile.of().build(ClassDesc.of("DefaultClass"), classBuilder -> {
                classBuilder.withMethodBody(oldMethod.methodName(), oldMethod.methodType(), oldMethod.flags().flagsMask(), codeBuilder -> {
                    if (returnInstructions.size() == 1) {
                        // TODO Question: is possible to have a method where the last instruction is not return?????
                        for (int i = 0; i < oldInstructions.size() -1; i++) {
                            codeBuilder.with(oldInstructions.get(i));  // add all the old instructions except the last return
                        }

                        if ( oldInstructions.size() > 1) {
                            CodeElement beforeLast = oldInstructions.get(oldInstructions.size() -2);
                            // if there is a label target before the last return instruction (meaning that the return is bound by a label) we need to add an
                            // instruction to replace the bind, otherwise the code builder will complain
                            if (beforeLast instanceof LabelTarget) {
                                codeBuilder.nop();
                            }
                        }
                    } else { // > 1
                        final Label newLabel = codeBuilder.newLabel();
                        final List<CodeElement> replacedInstructions = oldInstructions.stream()
                                .map(i -> {
                                    if (i instanceof ReturnInstruction) {
                                        return BranchInstruction.of(Opcode.GOTO, newLabel);
                                    } else {
                                        return i;
                                    }
                                }).toList();
                        for (CodeElement element: replacedInstructions) {
                            codeBuilder.with(element);
                        }

                        codeBuilder.labelBinding(newLabel);
                        codeBuilder.nop();  // we need an instruction to bind the label to, otherwise the code builder will complain
                    }
                });
            });

            return ClassFile.of().parse(transformedClass)
                    .methods().stream()
                    .filter(methodModel -> methodModel.methodName().equalsString(oldMethod.methodName().stringValue()))
                    .findFirst().orElseThrow();
        }
        return oldMethod;  // if there are no return there is nothing to change
    }
}
