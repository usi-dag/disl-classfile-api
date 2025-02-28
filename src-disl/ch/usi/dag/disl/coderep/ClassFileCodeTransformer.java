package ch.usi.dag.disl.coderep;

import java.lang.classfile.*;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.MethodTypeDesc;
import java.util.List;

public class ClassFileCodeTransformer {

    // transformer that does nothing, used in the stream().reduce() operation
    public static MethodTransform identityTransform = ClassFileBuilder::with;


    public static MethodModel applyTransformers(MethodModel oldMethod, List<MethodTransform> transforms) {
        if (oldMethod.parent().isEmpty()) {
            throw new RuntimeException("Method " +oldMethod.methodName().stringValue() + " has not class (method.parent())");
        }

        // first chain all the transform
        final MethodTransform transform = transforms.stream().reduce(identityTransform, MethodTransform::andThen);

        final String name = oldMethod.methodName().stringValue();
        final MethodTypeDesc methodDesc = oldMethod.methodTypeSymbol();

        // create a new class equivalent as the old one but with the method transformed
        byte[] transformedClass = ClassFile.of().transform(oldMethod.parent().get(), (classBuilder, element) -> {
            if (element instanceof MethodModel) {
                if (// if the name and signature are the same (there could be more methods with the same name)
                        ((MethodModel) element).methodName().equalsString(name) &&
                                ((MethodModel) element).methodTypeSymbol().equals(methodDesc))
                {
                    classBuilder.transformMethod((MethodModel) element, transform);
                }
            } else {
                classBuilder.with(element);
            }
        });

        // find the new transformed method and return it
        return ClassFile.of().parse(transformedClass).methods().stream()
                .filter(m -> m.methodName().equalsString(name) && m.methodTypeSymbol().equals(methodDesc))
                .findFirst()
                .orElseThrow();
    }


    // TODO this is a test to see if is possible to use ClassFile Transformer, since the transformers are chainable one after the other
    //  more transformer could be used for a single method
    public static MethodTransform replaceReturnsWithGoto = (methodBuilder, methodElement) -> {
        if (methodElement instanceof CodeModel) {
            List<CodeElement> oldInstructions = ((CodeModel) methodElement).elementList();
            final List<CodeElement> returnInstructions = oldInstructions.stream()
                    .filter(i -> i instanceof ReturnInstruction).toList();

            methodBuilder.withCode(codeBuilder -> {
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
        } else {
            methodBuilder.with(methodElement);
        }
    };

    public static MethodModel replaceReturnsWithGoto(final MethodModel oldMethod) {
        // TODO this method create a new class with the method edited and then return only the new method, there might be
        //  a better way to apply transformations, but in the ClassFile all element are immutable and to create new one you might need a
        //  builder. In this case it is essential to generate a new label.

        if (oldMethod.code().isEmpty()) {
            return oldMethod;  // Do nothing if there is no code
        }

        return applyTransformers(oldMethod, List.of(replaceReturnsWithGoto));
    }
}
