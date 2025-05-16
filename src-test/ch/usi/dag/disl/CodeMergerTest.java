package ch.usi.dag.disl;

import ch.usi.dag.disl.util.ClassFileHelper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.classfile.*;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeMergerTest {

    public static class Original {
        private int a;

        public Original() {
            a = 10;
        }

        public int getA() {
            return a;
        }

        public void setA(int a) {
            this.a = a;
        }
    }

    private final ClassModel original = TestUtils.__loadClass(Original.class);
    private final ClassModel instrumented = instrumentClass(original);

    @Test
    public void mergeOriginalCodeTest() {
        Set<String> changedMethods = original.methods().stream().map(ClassFileHelper::nameAndDescriptor).collect(Collectors.toSet());
        byte[] merged = CodeMerger.mergeOriginalCode(original, instrumented, changedMethods);
        // TODO this test was made to debug an exception thrown by "mergeOriginalCode", may need to also add some more robust testing for future regression tests
        Assert.assertTrue(merged.length > 0);

        ClassModel mergedClass = ClassFile.of().parse(merged);
        List<MethodModel> mergedMethods = mergedClass.methods();
        Assert.assertEquals(3, mergedMethods.size());

        List<MethodModel> originalMethods = original.methods();
        List<MethodModel> instrumentedMethods = instrumented.methods();

        for (MethodModel mergedMethod: mergedMethods) {
            final Utf8Entry methodName = mergedMethod.methodName();

            Optional<MethodModel> originalMethod = originalMethods.stream().filter(m -> m.methodName().equals(methodName)).findFirst();
            Optional<MethodModel> instrumentedMethod = instrumentedMethods.stream().filter(m -> m.methodName().equals(methodName)).findFirst();
            Assert.assertTrue(originalMethod.isPresent());
            Assert.assertTrue(instrumentedMethod.isPresent());

            List<CodeElement> mergedInstructions = mergedMethod.code().orElseThrow().elementList();
            List<CodeElement> originalInstructions = originalMethod.get().code().orElseThrow().elementList();
            List<CodeElement> instrumentedInstructions = instrumentedMethod.get().code().orElseThrow().elementList();

            Assert.assertTrue(mergedInstructions.size() >= originalInstructions.size() + instrumentedInstructions.size());
        }
    }


    /**
     * This method is for instrumenting the class "Original" that will then be tested
     * @param original the original class
     * @return the instrumented class to be tested
     */
    private static ClassModel instrumentClass(ClassModel original) {
        byte[] instrumented = ClassFile.of().transformClass(original,classTransform);
        return ClassFile.of().parse(instrumented);
    }

    private static final MethodTransform methodTransformInit = (methodBuilder, methodElement) -> {
        if (methodElement instanceof CodeModel codeModel) {
            methodBuilder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                if (codeElement instanceof ConstantInstruction.ArgumentConstantInstruction) {
                    codeBuilder.bipush(15); // replace the 10 with a 15 in the constructor.
                } else {
                    codeBuilder.with(codeElement);
                }
            });
        } else {
            methodBuilder.with(methodElement);
        }
    };

    private static final MethodTransform methodTransformGet = (methodBuilder, methodElement) -> {
        if (methodElement instanceof CodeModel codeModel) {
            methodBuilder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                if (codeElement instanceof ReturnInstruction returnInstruction) {
                    codeBuilder.bipush(2);  // return a + 2; in getA;
                    codeBuilder.iadd();
                    codeBuilder.with(returnInstruction);
                } else {
                    codeBuilder.with(codeElement);
                }
            });
        } else {
            methodBuilder.with(methodElement);
        }
    };

    private static final MethodTransform methodTransformSET = (methodBuilder, methodElement) -> {
        if (methodElement instanceof CodeModel codeModel) {
            methodBuilder.transformCode(codeModel, (codeBuilder, codeElement) -> {
                if (codeElement instanceof FieldInstruction fieldInstruction) {
                    codeBuilder.bipush(3); // this.a = a + 3;
                    codeBuilder.iadd();
                    codeBuilder.with(fieldInstruction);
                } else {
                    codeBuilder.with(codeElement);
                }
            });
        } else {
            methodBuilder.with(methodElement);
        }
    };

    private static final ClassTransform classTransform = (classBuilder, classElement) -> {
        if (classElement instanceof MethodModel methodModel) {
            switch (methodModel.methodName().stringValue()) {
                case "<init>" -> classBuilder.transformMethod(methodModel, methodTransformInit);
                case "getA" -> classBuilder.transformMethod(methodModel, methodTransformGet);
                case "setA" -> classBuilder.transformMethod(methodModel, methodTransformSET);
                default -> classBuilder.with(methodModel);
            }
        } else {
            classBuilder.with(classElement);
        }
    };

 }
