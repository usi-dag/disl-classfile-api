package ch.usi.dag.disl;

import ch.usi.dag.disl.util.ClassFileHelper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.classfile.*;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.List;
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
        // TODO this test was made to debug an exception thrown by "mergeOriginalCode", need to also add some check for correctness
        Assert.assertTrue(merged.length > 0);
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
