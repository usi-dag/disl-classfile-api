package ch.usi.dag.disl;

import java.lang.classfile.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import ch.usi.dag.disl.dynamicbypass.BypassCheck;
import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.util.AsmHelper;
import ch.usi.dag.disl.util.ClassFileHelper;

import static java.lang.constant.ConstantDescs.CD_Boolean;


abstract class CodeMerger {

    private static final ClassDesc BPC_CLASS = ClassDesc.ofDescriptor(BypassCheck.class.descriptorString());

    private static final String BPC_METHOD = "executeUninstrumented";

    private static final MethodTypeDesc BPC_DESC = MethodTypeDesc.of(CD_Boolean);

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
                                List<CodeElement> instrumentedInstructions = instrumentedMethod.code().orElseThrow().elementList();
                                for (CodeElement instruction: instrumentedInstructions) {
                                    blockCodeBuilder.with(instruction);
                                }
                            }, blockCodeBuilder -> {
                                List<CodeElement> originalInstructions = originalMethod.code().orElseThrow().elementList();
                                for (CodeElement instruction: originalInstructions) {
                                    blockCodeBuilder.with(instruction);
                                }
                            });
                        }
                    );
                } else {
                    classBuilder.with(classElement);
                }
            }
        });
    }

    // NOTE: the origCN and instCN nodes will be destroyed in the process
    // NOTE: abstract or native methods should not be included in the
    // changedMethods list
    public static ClassNode fixupLongMethods (
        final boolean splitLongMethods, final ClassNode origCN,
        final ClassNode instCN
    ) {
        //
        // Choose a fix-up strategy and process all over-size methods in the
        // instrumented class.
        //
        final IntConsumer fixupStrategy = splitLongMethods ?
            i -> __splitLongMethod (i, instCN, origCN) :
            i -> __revertToOriginal (i, instCN, origCN);

        IntStream.range (0, instCN.methods.size ()).parallel ().unordered ()
            .filter (i -> __methodSize (instCN.methods.get (i)) > ALLOWED_SIZE)
            .forEach (i -> fixupStrategy.accept (i));

        return instCN;
    }


    private static void __splitLongMethod (
        final int methodIndex, final ClassNode instCN, final ClassNode origCN
    ) {
        // TODO jb ! add splitting for to long methods
        // - ignore clinit - output warning
        // - output warning if splitted is to large and ignore

        // check the code size of the instrumented method
        // add if to the original method that jumps to the renamed instrumented
        // method
        // add original method to the instrumented code
        // rename instrumented method
    }


    private static void __revertToOriginal (
        final int instIndex, final ClassNode instCN, final ClassNode origCN
    ) {
        //
        // Replace the instrumented method with the original method,
        // and print a warning about it.
        //
        final MethodNode instMN = instCN.methods.get (instIndex);
        final MethodNode origMN = __findMethodNode (origCN, instMN.name, instMN.desc);
        instCN.methods.set (instIndex, origMN);

        System.err.printf (
            "warning: method %s.%s%s not instrumented, because its size "+
            "(%d) exceeds the maximal allowed method size (%d)\n",
            AsmHelper.typeName (instCN), instMN.name, instMN.desc,
            __methodSize (instMN), ALLOWED_SIZE
        );
    }


    private static int __methodSize (final MethodNode method) {
        final CodeSizeEvaluator cse = new CodeSizeEvaluator (null);
        method.accept (cse);
        return cse.getMaxSize ();
    }


    private static MethodNode __findMethodNode (
        final ClassNode cn, final String name, final String desc
    ) {
        return cn.methods.parallelStream ().unordered ()
            .filter (m -> m.name.equals (name) && m.desc.equals (desc))
            .findAny ().orElseThrow (() -> new RuntimeException (
                "Code merger fatal error: method for merge not found"
            ));
    }

}
