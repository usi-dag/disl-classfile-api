package ch.usi.dag.disl.coderep;

import ch.usi.dag.disl.annotation.SyntheticLocal;
import ch.usi.dag.disl.localvar.AbstractLocalVar;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.ReflectionHelper;

import java.io.PrintStream;
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.constant.ConstantDescs.*;

public class ClassFileCodeTransformer {

    // transformer that does nothing, used in the stream().reduce() operation
    public static MethodTransform identityTransform = ClassFileBuilder::with;


    public static MethodModel applyTransformers(MethodModelCopy oldMethod, List<MethodTransform> transforms) {
        // first chain all the transform
        final MethodTransform transform = transforms.stream().reduce(identityTransform, MethodTransform::andThen);
        return applyTransformers2(oldMethod, transform);
    }

    public static MethodModel applyTransformers(MethodModelCopy oldMethod, MethodTransform transform) {
        if (oldMethod.parent().isEmpty()) {
            throw new RuntimeException("Method " +oldMethod.methodName().stringValue() + " has not class (method.parent())");
        }

        final String name = oldMethod.methodName().stringValue();
        final MethodTypeDesc methodDesc = oldMethod.methodTypeSymbol();

        // create a new class equivalent as the old one but with the method transformed
        byte[] transformedClass = ClassFile.of().transformClass(oldMethod.parent().get(), (classBuilder, element) -> {
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

    /**
     * A method is transformed using first the list of instructions in MethodModelCopy instead of the instructions of the
     * original MethodModel
     * @param oldMethod the method to transform
     * @param methodTransform the transform to apply
     * @return the new MethodModel
     */
    public static MethodModel applyTransformers2(MethodModelCopy oldMethod, MethodTransform methodTransform) {
        final String name = oldMethod.methodName().stringValue();
        final MethodTypeDesc methodDesc = oldMethod.methodTypeSymbol();
        final ClassDesc tempClass = ClassDesc.of("TempClass");
        byte[] transformedClass = ClassFile.of().build(tempClass, classBuilder -> {
            MethodTransform firstTransform = makeMethodWithInstructions(oldMethod.instructions());
            classBuilder.transformMethod(oldMethod.getOriginal(), firstTransform.andThen(methodTransform));
        });

        return ClassFile.of().parse(transformedClass).methods().stream()
                .filter(m -> m.methodName().equalsString(name) && m.methodTypeSymbol().equals(methodDesc))
                .findFirst()
                .orElseThrow();
    }


    // various Transformers:

    /**
     * this just add the code in the method
     * @param instructions list of instructions
     * @return a transform that just add the code in the method
     */
    public static MethodTransform makeMethodWithInstructions(List<CodeElement> instructions) {
        return ((builder, element) -> {
            if (element instanceof CodeModel) {
                builder.withCode(codeBuilder -> {
                    for (CodeElement instruction: instructions) {
                        codeBuilder.with(instruction);
                    }
                });
            } else {
                builder.with(element);
            }
        });
    }

    /**
     * insert an exception handler around the code
     * @param location string to print if there is an exception
     * @return the MethodTransform
     */
    public static MethodTransform InsertExceptionHandlerCodeTransformer(String location) {
        // The inserted code:
        //
        // TRY_BEGIN:       try {
        //                      ... original snippet code ...
        //                      goto HANDLER_END;
        // TRY_END:         } finally (e) {
        // HANDLER_BEGIN:       System.err.println(...);
        //                      e.printStackTrace();
        //                      System.exit(666);
        //                      throw e;
        // HANDLER_END:     }
        final ClassDesc printStreamDesc = ClassDesc.ofDescriptor(PrintStream.class.descriptorString());
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {
                methodBuilder.withCode(codeBuilder -> {

                    codeBuilder
                            .trying(tryHandler -> {
                                for (CodeElement instruction: codeModel.elementList()) {
                                    tryHandler.with(instruction); // add all the original instructions
                                }
                            }, catchHandler -> {
                                catchHandler.catchingAll(blockCodeBuilder -> {
                                    // The caught exception will be on top of the operand stack when the catch block is entered.

                                    // get print class
                                    codeBuilder.getstatic(ClassDesc.ofDescriptor(System.class.descriptorString()), "err", printStreamDesc);
                                    // load constant
                                    blockCodeBuilder.loadConstant(String.format("%s: failed to handle an exception", location)); // load string
                                    // print string
                                    codeBuilder.invokevirtual(printStreamDesc, "println", MethodTypeDesc.of(CD_void, CD_String));

                                    codeBuilder.dup();
                                    // print stack trace
                                    codeBuilder.invokevirtual(ClassDesc.ofDescriptor(Throwable.class.descriptorString()), "printStackTrace", MethodTypeDesc.of(CD_void));
                                    // system exit
                                    codeBuilder.loadConstant(666);
                                    codeBuilder.invokestatic(ClassDesc.ofDescriptor(System.class.descriptorString()), "exit", MethodTypeDesc.of(CD_void, CD_int));
                                    // throw exception
                                    codeBuilder.athrow();
                                });
                            })
                            .nop(); // TODO verify that this nop is necessary
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }


    /**
     * Shifts access to local variable slots by a given offset.
     * @param offset the offset to apply
     * @return the MethodTransform
     */
    public static MethodTransform shiftLocalVarSlotCodeTransformer(final int offset) {
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {
                methodBuilder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                    switch (codeElement) {
                        case LoadInstruction loadInstruction -> {
                            final int newSlot = Math.max(0, loadInstruction.slot() + offset);
                            LoadInstruction withOffset = LoadInstruction.of(loadInstruction.typeKind(), newSlot);
                            codeBuilder.with(withOffset);
                        }
                        case StoreInstruction storeInstruction -> {
                            int newSlot = Math.max(0,storeInstruction.slot() + offset);
                            StoreInstruction withOffset = StoreInstruction.of(storeInstruction.typeKind(), newSlot);
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
    public static MethodTransform insertDynamicBypassControlCodeTransformer() {
        final Method __dbActivate__ = ReflectionHelper.getMethod (ch.usi.dag.disl.dynamicbypass.DynamicBypass.class, "activate");
        final Method __dbDeactivate__ = ReflectionHelper.getMethod (ch.usi.dag.disl.dynamicbypass.DynamicBypass.class, "deactivate");
        final ClassDesc ownerDesc = ClassDesc.ofDescriptor(ch.usi.dag.disl.dynamicbypass.DynamicBypass.class.descriptorString());

        return  (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {
                methodBuilder.withCode(codeBuilder -> {
                    // add invocation at the beginning
                    codeBuilder.invokestatic(
                            ownerDesc,
                            "activate",
                            ClassFileHelper.getMethodDescriptor(__dbActivate__),
                            false
                            );
                    // add all other elements
                    for (CodeElement codeElement: codeModel.elementList()) {
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

    /**
     * Transform the access of the slv from static to local, the method descriptor and flags are used to compute the
     * max locals
     * @param syntheticLocalVars set of synthetic local variables
     * @param methodTypeDesc descriptor of the method
     * @param flags access flags of the methods
     * @return the MethodTransform
     */
    public static MethodTransform static2Local(final Set<SyntheticLocalVar> syntheticLocalVars, MethodTypeDesc methodTypeDesc, AccessFlags flags) {

        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {

                // this should give a copy so we do not touch the original list
                List<CodeElement> instructions = new ArrayList<>(codeModel.elementList());

                // since the classFile api does not compute automatically the max locals until the last transformation there is
                // no guarantee that we can get them here from the methodModem (parent of the codeModel) so we compute them manually
                int maxLocals = ClassFileHelper.getMaxLocals(instructions, methodTypeDesc, flags);

                final CodeElement first = instructions.getFirst();
                // Insert code to initialize synthetic local variables (unless marked to
                // be left uninitialized) at the beginning of a method.
                for (SyntheticLocalVar slv: syntheticLocalVars) {
                    if (slv.getInitialize() == SyntheticLocal.Initialize.NEVER) {
                        continue;
                    }

                    // If the variable has initialization code, just copy it. It will
                    // still refer to the static variable, but that will be fixed in
                    // the next step.
                    if (slv.hasInitCode()) {
                        ClassFileHelper.insertAllBefore(first, slv.getInitCodeList(), instructions);
                    } else {

                        // Otherwise, just initialize it with a default value depending
                        // on its type. The default value for arrays is null, like for
                        // objects, but they also need an additional CHECKCAST
                        // instruction to make the verifier happy.
                        ClassDesc classDesc = slv.getType();
                        TypeKind typeKind = slv.getTypeKind();
                        if (!classDesc.isArray()) {
                            ClassFileHelper.insertBefore(
                                    first,
                                    ClassFileHelper.loadDefault(typeKind),
                                    instructions);
                        } else {
                            ClassFileHelper.insertBefore(
                                    first,
                                    ClassFileHelper.loadNull(),
                                    instructions);
                            ClassFileHelper.insertBefore(
                                    first,
                                    ClassFileHelper.checkCast(classDesc),
                                    instructions);
                        }

                        // For now, just issue an instruction to store the value into
                        // the original static field. The transformation to local
                        // variable comes in the next step.
                        ClassFileHelper.insertBefore(
                                first,
                                ClassFileHelper.putStatic(ClassDesc.of(slv.getOwner()).descriptorString(), slv.getName(), classDesc.descriptorString()),
                                instructions);
                    }
                }

                // Scan the method code for GETSTATIC/PUTSTATIC instructions accessing
                // the static fields marked as synthetic locals. Replace all the
                // static accesses with local variables.
                // TODO LB: iterate over a copy unless we are sure an iterator is OK
                for (CodeElement codeElement: instructions.toArray(instructions.toArray(new CodeElement[0]))) {
                    if (!(codeElement instanceof FieldInstruction fieldInstruction) ||
                            !ClassFileHelper.isStaticFieldAccess(codeElement)) {
                        continue;
                    }
                    final String varId = SyntheticLocalVar.fqFieldNameFor(
                            fieldInstruction.owner().name().stringValue(),
                            fieldInstruction.name().stringValue()
                    );

                    // Try to find the static field being accessed among the synthetic
                    // local fields, and determine the local variable index and local
                    // slot index while doing that.
                    int index = 0, count = 0;
                    for (final SyntheticLocalVar var: syntheticLocalVars) {
                        if (varId.equals(var.getID())) {
                            break;
                        }
                        index += var.getTypeKind().slotSize();
                        count++;
                    }

                    if (count == syntheticLocalVars.size()) {
                        // Static field not found among the synthetic locals.
                        continue;
                    }

                    // Replace the static field access with local variable access.
                    final ClassDesc type = fieldInstruction.typeSymbol();
                    final TypeKind typeKind = TypeKind.from(type);
                    final int slot = maxLocals + index;
                    final Opcode opcode = fieldInstruction.opcode();

                    ClassFileHelper.insertBefore(
                            fieldInstruction,
                            (opcode == Opcode.GETSTATIC) ?
                                    ClassFileHelper.loadVar(typeKind, slot) :
                                    ClassFileHelper.storeVar(typeKind, slot),
                            instructions
                    );

                    instructions.remove(fieldInstruction);
                }

                methodBuilder.withCode(codeBuilder -> {
                    for (CodeElement codeElement: instructions) {
                        codeBuilder.with(codeElement);
                    }
                });

            } else {
                methodBuilder.with(methodElement);
            }
        };
    }

    public static MethodTransform rewriteThreadLocalVarAccessesCodeTransformer(final Set<ThreadLocalVar> tlvs) {
        final Set<String> tlvID = tlvs.stream().map(AbstractLocalVar::getID).collect(Collectors.toSet());
        final ClassDesc threadDesc = ClassDesc.ofDescriptor(Thread.class.descriptorString());
        final String currentThreadName = "currentThread";
        MethodTypeDesc methodTypeDesc = MethodTypeDesc.of(threadDesc);

        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel) {

                final List<CodeElement> originalInstructions = ((CodeModel) methodElement).elementList();

                List<CodeElement> modifiedInstructions = new ArrayList<>();


                for (CodeElement instruction: originalInstructions) {
                    if (instruction instanceof FieldInstruction fieldInstruction) {
                        // check that the variable is a thread local variable
                        if (!tlvID.contains(ThreadLocalVar.fqFieldNameFor(fieldInstruction.owner().name().stringValue(), fieldInstruction.name().stringValue()))) {
                            modifiedInstructions.add(fieldInstruction);
                            continue;
                        }
                        final InvokeInstruction currentThreadInstruction = ClassFileHelper.invokeStatic(threadDesc.descriptorString(), currentThreadName, methodTypeDesc.descriptorString());
                        switch (fieldInstruction.opcode()) {
                            case Opcode.GETSTATIC -> {
                                // Issue a call to Thread.currentThread() and access a field
                                // in the current thread corresponding to the thread-local variable.
                                modifiedInstructions.add(currentThreadInstruction);
                                modifiedInstructions.add(
                                    ClassFileHelper.getField(
                                            threadDesc.descriptorString(),
                                            fieldInstruction.name().stringValue(),
                                            fieldInstruction.typeSymbol().descriptorString())
                                );
                            }
                            case Opcode.PUTSTATIC -> {
                                Instruction previous = ClassFileHelper.firstPreviousRealInstruction(modifiedInstructions, modifiedInstructions.getLast());
                                if (__isSimpleLoad(previous)) {
                                    final int index = modifiedInstructions.indexOf(previous);
                                    modifiedInstructions.add(index, currentThreadInstruction);
                                } else {
                                    modifiedInstructions.add(currentThreadInstruction);
                                    if (TypeKind.from(fieldInstruction.typeSymbol()).slotSize() == 1) {
                                        modifiedInstructions.add(StackInstruction.of(Opcode.SWAP));
                                    } else {
                                        modifiedInstructions.add(StackInstruction.of(Opcode.DUP_X2));
                                        modifiedInstructions.add(StackInstruction.of(Opcode.POP));
                                    }
                                }
                                modifiedInstructions.add(
                                        ClassFileHelper.putField(
                                                threadDesc.descriptorString(),
                                                fieldInstruction.name().stringValue(),
                                                fieldInstruction.typeSymbol().descriptorString())
                                );
                            }
                            default -> modifiedInstructions.add(fieldInstruction);
                        }
                    } else {
                        modifiedInstructions.add(instruction);
                    }
                }

                // the reason why all element are added at once at the end it that in some cases we might need to insert an instruction before another previous one
                // with the codeBuilder we can only add instructions at the very end
                methodBuilder.withCode(codeBuilder -> {
                    for (CodeElement instruction: modifiedInstructions) {
                        codeBuilder.with(instruction);
                    }
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }

    private static boolean __isSimpleLoad(final Instruction instruction) {
        final int opcode = instruction.opcode().bytecode();
        return (opcode >= Opcode.ACONST_NULL.bytecode() && opcode <= Opcode.LDC.bytecode()) ||
                (opcode >= Opcode.ILOAD.bytecode() && opcode <= Opcode.ALOAD.bytecode()) ||
                opcode == Opcode.GETSTATIC.bytecode();
    }


    // non optimised version
    public static MethodTransform rewriteThreadLocalVarAccessesCodeTransformer2(final Set<ThreadLocalVar> tlvs) {
        final Set<String> tlvID = tlvs.stream().map(AbstractLocalVar::getID).collect(Collectors.toSet());
        final ClassDesc threadDesc = ClassDesc.ofDescriptor(Thread.class.descriptorString());
        final String currentThreadName = "currentThread";
        MethodTypeDesc methodTypeDesc = MethodTypeDesc.of(threadDesc);

        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel) {
                methodBuilder.transformCode((CodeModel) methodElement, (codeBuilder, codeElement) -> {
                    if (codeElement instanceof FieldInstruction fieldInstruction) {
                        if (tlvID.contains(ThreadLocalVar.fqFieldNameFor(fieldInstruction.owner().name().stringValue(), fieldInstruction.name().stringValue()))) {
                            final InvokeInstruction currentThreadInstruction = ClassFileHelper.invokeStatic(
                                    threadDesc.descriptorString(),
                                    currentThreadName,
                                    methodTypeDesc.descriptorString());
                            final FieldInstruction putFieldInstruction = ClassFileHelper.putField(
                                    threadDesc.descriptorString(),
                                    fieldInstruction.name().stringValue(),
                                    fieldInstruction.typeSymbol().descriptorString());

                            final FieldInstruction getFieldInstruction = ClassFileHelper.getField(
                                    threadDesc.descriptorString(),
                                    fieldInstruction.name().stringValue(),
                                    fieldInstruction.typeSymbol().descriptorString());

                            switch (fieldInstruction.opcode()) {
                                case Opcode.GETSTATIC -> {
                                    codeBuilder.with(currentThreadInstruction);
                                    codeBuilder.with(getFieldInstruction);
                                }
                                case Opcode.PUTSTATIC -> {
                                    codeBuilder.with(currentThreadInstruction);
                                    if (TypeKind.from(fieldInstruction.typeSymbol()).slotSize() == 1) {
                                        codeBuilder.swap();
                                    } else {
                                        codeBuilder.dup_x2();
                                        codeBuilder.pop();
                                    }
                                    codeBuilder.with(putFieldInstruction);
                                }
                                default -> codeBuilder.with(fieldInstruction);
                            }
                        } else {
                            codeBuilder.with(fieldInstruction);
                        }
                    } else {
                        codeBuilder.with(codeElement);
                    }
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }


    /**
     * replace all return instruction with a GOTO to the end of the snippet
     * @return the MethodTransform
     */
    public static MethodTransform replaceReturnsWithGoto() {
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {
                List<CodeElement> oldInstructions = codeModel.elementList();
                final List<CodeElement> returnInstructions = oldInstructions.stream()
                        .filter(i -> i instanceof ReturnInstruction).toList();

                methodBuilder.withCode(codeBuilder -> {
                    if (returnInstructions.size() == 1) {
                        // The return instruction might not be the last instruction, instead it could be a LabelTarget
                        // remove the return
                        List<CodeElement> withoutReturn = oldInstructions.stream().filter(c -> !(c instanceof ReturnInstruction)).toList();
                        for (CodeElement element: withoutReturn) {
                            codeBuilder.with(element);
                        }
                        if (withoutReturn.getLast() instanceof LabelTarget) {
                            codeBuilder.nop(); // add a nop otherwise the builder might trow since the label would be unbounded TODO is still necessary???
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
                        codeBuilder.nop();  // we need an instruction to bind the label to, otherwise the code builder will complain TODO is still necessary
                    }
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }

    public static MethodTransform removeLineNumbersAndLocalVariableInfo() {
        return (methodBuilder, methodElement) -> {
            if (methodElement instanceof CodeModel codeModel) {
                methodBuilder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                    if (!(codeElement instanceof LineNumber || codeElement instanceof LocalVariable || codeElement instanceof LocalVariableType)) {
                        codeBuilder.with(codeElement);
                    }
                });
            } else {
                methodBuilder.with(methodElement);
            }
        };
    }

    public static MethodModelCopy replaceReturnsWithGoto(final MethodModelCopy oldMethod) {
        // TODO this method create a new class with the method edited and then return only the new method, there might be
        //  a better way to apply transformations, but in the ClassFile all element are immutable and to create new one you might need a
        //  builder. In this case it is essential to generate a new label.
        if (!oldMethod.hasCode()) {
            return oldMethod;  // Do nothing if there is no code
        }
        return new MethodModelCopy(applyTransformers2(oldMethod, replaceReturnsWithGoto().andThen(removeLineNumbersAndLocalVariableInfo())));
    }

    private static byte[] removeNopInstructions(byte[] before) {
        ClassModel classModel = ClassFile.of(ClassFile.LineNumbersOption.DROP_LINE_NUMBERS).parse(before);

        return ClassFile.of() // potentially some option can be passed here
                .transformClass(classModel, (classBuilder, classElement) ->
                {
                    if (classElement instanceof MethodModel methodModel) {
                        classBuilder.transformMethod(methodModel, ClassFileCodeTransformer.removeNopInstructions());
                    } else {
                        classBuilder.with(classElement);
                    }
                });
    }

    public static MethodTransform removeNopInstructions() {
        return ((builder, element) -> {
            if (element instanceof CodeModel codeModel) {
                builder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                    if (!(codeElement instanceof NopInstruction)) {
                        codeBuilder.with(codeElement);
                    }
                });
            } else {
                builder.with(element);
            }
        });
    }

}
