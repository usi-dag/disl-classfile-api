package ch.usi.dag.disl.coderep;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.ReflectionHelper;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.List;

public class ClassFileCodeTransformer {

    // transformer that does nothing, used in the stream().reduce() operation
    public static MethodTransform identityTransform = ClassFileBuilder::with;


    public static MethodModel applyTransformers(MethodModel oldMethod, List<MethodTransform> transforms) {
        // first chain all the transform
        final MethodTransform transform = transforms.stream().reduce(identityTransform, MethodTransform::andThen);
        return applyTransformers(oldMethod, transform);
    }

    public static MethodModel applyTransformers(MethodModel oldMethod, MethodTransform transform) {
        if (oldMethod.parent().isEmpty()) {
            throw new RuntimeException("Method " +oldMethod.methodName().stringValue() + " has not class (method.parent())");
        }

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

    // various Transformers:


    /**
     * Shifts access to local variable slots by a given offset.
     * @param offset the offset to apply
     * @return the MethodTransform
     */
    public static MethodTransform shiftLocalVarSlotCodeTransformer(final int offset) {
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel) {
                methodBuilder.transformCode((CodeModel) methodElement, (codeBuilder, codeElement) -> {
                    switch (codeElement) {
                        case LoadInstruction loadInstruction -> {
                            LoadInstruction withOffset = LoadInstruction.of(loadInstruction.opcode(), loadInstruction.slot() + offset);
                            codeBuilder.with(withOffset);
                        }
                        case StoreInstruction storeInstruction -> {
                            StoreInstruction withOffset = StoreInstruction.of(storeInstruction.opcode(), storeInstruction.slot() + offset);
                            codeBuilder.with(withOffset);
                        }
                        case DiscontinuedInstruction.RetInstruction retInstruction -> {  // this instruction is unlikely to happen, but the asm version handle it
                            DiscontinuedInstruction.RetInstruction withOffset = DiscontinuedInstruction.RetInstruction.of(retInstruction.opcode(), retInstruction.slot() + offset);
                            codeBuilder.with(withOffset);
                        }
                        case IncrementInstruction incrementInstruction -> {
                            IncrementInstruction withOffset = IncrementInstruction.of(incrementInstruction.slot() + offset, incrementInstruction.constant());
                            codeBuilder.with(withOffset);
                        }
                        default -> codeBuilder.with(codeElement);
                    }
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }


    /**
     * Wraps a sequence of instructions with code that controls the dynamic bypass.
     * The bypass is enabled before the first instruction and disabled again after
     * the last instruction:
     *
     * <pre>
     *   DynamicBypass.activate();
     *   ... original snippet code ...
     *   DynamicBypass.deactivate();
     * </pre>
     * @return the MethodTransform
     */
    public static MethodTransform InsertDynamicBypassControlCodeTransformer() {
        final Method __dbActivate__ = ReflectionHelper.getMethod (ch.usi.dag.disl.dynamicbypass.DynamicBypass.class, "activate");
        final Method __dbDeactivate__ = ReflectionHelper.getMethod (ch.usi.dag.disl.dynamicbypass.DynamicBypass.class, "deactivate");
        final ClassDesc ownerDesc = ClassDesc.ofDescriptor(ch.usi.dag.disl.dynamicbypass.DynamicBypass.class.descriptorString());

        return  (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel) {
                methodBuilder.withCode(codeBuilder -> {
                    // add invocation at the beginning
                    codeBuilder.invokestatic(
                            ownerDesc,
                            "activate",
                            ClassFileHelper.getMethodDescriptor(__dbActivate__),
                            false
                            );
                    // add all other elements
                    for (CodeElement codeElement: ((CodeModel) methodElement).elementList()) {
                        codeBuilder.with(codeElement);
                    }
                    // add invocation at the end
                    codeBuilder.invokestatic(
                            ownerDesc,
                            "deactivate",
                            ClassFileHelper.getMethodDescriptor(__dbDeactivate__),
                            false
                    );
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }


    // TODO this is a test to see if is possible to use ClassFile Transformer, since the transformers are chainable one after the other
    //  more transformer could be used for a single method
    /**
     * replace all return instruction with a GOTO to the end of the snippet
     * @return the MethodTransform
     */
    public static MethodTransform replaceReturnsWithGoto() {
        return (methodBuilder, methodElement) -> {
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
    }



    public static MethodModel replaceReturnsWithGoto(final MethodModel oldMethod) {
        // TODO this method create a new class with the method edited and then return only the new method, there might be
        //  a better way to apply transformations, but in the ClassFile all element are immutable and to create new one you might need a
        //  builder. In this case it is essential to generate a new label.
        if (oldMethod.code().isEmpty()) {
            return oldMethod;  // Do nothing if there is no code
        }
        return applyTransformers(oldMethod, replaceReturnsWithGoto());
    }
}
