package ch.usi.dag.disl;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ch.usi.dag.disl.dynamicbypass.BypassCheck;
import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.util.ClassFileHelper;

import static java.lang.constant.ConstantDescs.CD_boolean;


abstract class CodeMerger {

    private static final ClassDesc BPC_CLASS = ClassDesc.ofDescriptor(BypassCheck.class.descriptorString());

    private static final String BPC_METHOD = "executeUninstrumented";

    private static final MethodTypeDesc BPC_DESC = MethodTypeDesc.of(CD_boolean);

    private static final int ALLOWED_SIZE = 64 * 1024; // 64KB limit


    // NOTE: abstract/native methods should not be included in changedMethods list
    // TODO the indentation is quite extreme, if there is time refactor by using transformers
    public static byte[] mergeOriginalCode(final ClassModel originalClass,
                                           final ClassModel instrumentedClass,
                                           final Set<String> changedMethods) {
        if (changedMethods == null) {
            throw new DiSLFatalException ("Set of changed methods cannot be null");
        }
        List<MethodModel> originalMethods = originalClass.methods();
        Map<String, MethodModel> methodModelMap = originalMethods.stream().collect(Collectors.toMap(
                ClassFileHelper::nameAndDescriptor, m -> m
        ));

        return ClassFile.of().build(instrumentedClass.thisClass().asSymbol(), classBuilder -> {
            for (ClassElement classElement: instrumentedClass) {
                if (classElement instanceof MethodModel instrumentedMethod) {

                    String methodIdentifier = ClassFileHelper.nameAndDescriptor(instrumentedMethod);

                    MethodModel originalMethod = methodModelMap.get(methodIdentifier);

                    if (originalMethod == null) { // TODO should I just pass the classElement to the builder instead and not throw???
                        throw new RuntimeException("Error while merging code, cannot find matching names for method " + instrumentedMethod.methodName());
                    }

                    // The bypass check code has the following layout:
                    //
                    //     if (!BypassCheck.executeUninstrumented ()) {
                    //         <instrumented code>
                    //     } else {
                    //         <original code>
                    //     }
                    classBuilder.withMethodBody(instrumentedMethod.methodName(), instrumentedMethod.methodType(), instrumentedMethod.flags().flagsMask(),
                        codeBuilder -> {
                            // first invoke the method that will put the boolean result on the stack
                            codeBuilder.invokestatic(BPC_CLASS, BPC_METHOD, BPC_DESC);
                            // execute the "then" if the stack has on top true, else it will execute the "else" block
                            codeBuilder.ifThenElse(blockCodeBuilder -> {
                                List<CodeElement> originalInstructions = originalMethod.code().orElseThrow().elementList();
                                for (CodeElement instruction: originalInstructions) {
                                    blockCodeBuilder.with(instruction);
                                }
                            }, blockCodeBuilder -> {
                                List<CodeElement> instrumentedInstructions = instrumentedMethod.code().orElseThrow().elementList();
                                for (CodeElement instruction: instrumentedInstructions) {
                                    blockCodeBuilder.with(instruction);
                                }
                            });
                            // the function "codeBuilder.ifThenElse(...) will automatically add a GOTO between the two blocks, so it could occur that
                            // the resulting instruction look like the following:
//                            0: {opcode: INVOKESTATIC, owner: ch/usi/dag/disl/dynamicbypass/BypassCheck, method name: executeUninstrumented, method type: ()Ljava/lang/Boolean;}
//                            3: {opcode: IFEQ, target: 14}
//                            6: {opcode: ALOAD_0, slot: 0}
//                            7: {opcode: INVOKESPECIAL, owner: java/lang/Object, method name: <init>, method type: ()V}
//                            10: {opcode: RETURN}
//                            11: {opcode: GOTO, target: 19}
//                            14: {opcode: ALOAD_0, slot: 0}
//                            15: {opcode: INVOKESPECIAL, owner: java/lang/Object, method name: <init>, method type: ()V}
//                            18: {opcode: RETURN}
                            // this causes an exception since the target of the GOTO is out of bound, to solve this we need to add another return
                            // at the end, since is not possible to remove the GOTO after the codeBuilder is invoked.
                            // TODO Since this problem causes to have an extra GOTO and RETURN instructions it is possible to be optimized further
                            //  by not using the "codeBuilder.ifThenElse(...)", instead doing it manually.
                            codeBuilder.return_(TypeKind.from(instrumentedMethod.methodTypeSymbol().returnType()));
                        }
                    );
                } else {
                    classBuilder.with(classElement);
                }
            }
        });
    }

    // return the sum of all the instructions (in bytes), does not include any pseudo instruction
    private static int getEstimateMethodSize(MethodModel method) {
        if (method.code().isEmpty()) {
            return 0;
        }
        return method.code().get().elementStream().mapToInt(e -> {
            if (e instanceof Instruction instruction) {
                return instruction.sizeInBytes();
            }
            return 0;
        }).sum();
    }

    public static byte[] fixLongMethods(final ClassModel classModel, final ClassModel originalClassModel) {

        // TODO this only replace methods that are too long with the original method, but it might be
        //  better to split the method in two
        List<String> methodsToFix = classModel.methods().stream()
                .filter(methodModel -> getEstimateMethodSize(methodModel) > ALLOWED_SIZE)
                .map(ClassFileHelper::nameAndDescriptor).toList();

        if (methodsToFix.isEmpty()) {
            return null;
        }

        final Map<String, MethodModel> originalsMethods = originalClassModel.methods().stream().collect(Collectors.toMap(
                ClassFileHelper::nameAndDescriptor,
                methodModel -> methodModel
        ));

        return ClassFile.of().build(classModel.thisClass().asSymbol(), classBuilder -> {
            for (ClassElement classElement: classModel) {
                if (classElement instanceof MethodModel methodModel) {
                    if (methodsToFix.contains(ClassFileHelper.nameAndDescriptor(methodModel))) {
                        MethodModel originalMethod = originalsMethods.get(ClassFileHelper.nameAndDescriptor(methodModel));

                        if (originalMethod == null) {
                            throw new RuntimeException("Cannot match method " + methodModel.methodName());
                        }
                        classBuilder.with(originalMethod);
                        System.err.printf (
                                "warning: method %s.%s%s not instrumented, because its size "+
                                        "(%d) exceeds the maximal allowed method size (%d)\n",
                                classModel.thisClass().asInternalName(), methodModel.methodName(), methodModel.methodType(),
                                getEstimateMethodSize(methodModel), ALLOWED_SIZE
                        );
                    } else {
                        classBuilder.with(classElement);
                    }
                } else {
                    classBuilder.with(classElement);
                }
            }
        });
    }
}
