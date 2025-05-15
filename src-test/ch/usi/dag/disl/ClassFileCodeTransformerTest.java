package ch.usi.dag.disl;

import ch.usi.dag.disl.coderep.ClassFileCodeTransformer;
import ch.usi.dag.disl.util.ClassFileAPI.ClassModelHelper;
import ch.usi.dag.disl.util.MethodModelCopy;
import org.junit.Assert;
import org.junit.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassFileCodeTransformerTest {

    // every method name must be unique
    public class TestClass {
        public static int singleReturn(int a) {
            return a + 1;
        }

        public static int multiReturn(int a) {
            if (a < 0) {
                return 0;
            } else if (a == 0) {
                return a + 1;
            }
            return a;
        }

        public static int recursiveRet(int a) {
            if (a <= 0) {
                return 0;
            }
            return a + recursiveRet(a - 1);
        }
    }

    final Map<String, MethodModelCopy> methodModelCopyMap = makeMap();

    @Test
    public void replaceReturnWithGotoTest() {
        MethodTransform methodTransform = ClassFileCodeTransformer.replaceReturnsWithGoto();

        MethodModel transformed = ClassFileCodeTransformer.applyTransformers2(methodModelCopyMap.get("singleReturn"), methodTransform);
        for (CodeElement element: transformed.code().orElseThrow().elementList()) {
            Assert.assertFalse(element instanceof ReturnInstruction);
        }

        MethodModel transformed2 = ClassFileCodeTransformer.applyTransformers2(methodModelCopyMap.get("multiReturn"), methodTransform);
        for (CodeElement element: transformed2.code().orElseThrow().elementList()) {
            Assert.assertFalse(element instanceof ReturnInstruction);
        }

        MethodModel transformed3 = ClassFileCodeTransformer.applyTransformers2(methodModelCopyMap.get("recursiveRet"), methodTransform);
        for (CodeElement element: transformed3.code().orElseThrow().elementList()) {
            Assert.assertFalse(element instanceof ReturnInstruction);
        }
    }

    private static Map<String, MethodModelCopy> makeMap() {
        ClassModel classModel = TestUtils.__loadClass(TestClass.class);
        return classModel.methods().stream().collect(Collectors.toMap(m -> m.methodName().stringValue(), MethodModelCopy::new));
    }

}
