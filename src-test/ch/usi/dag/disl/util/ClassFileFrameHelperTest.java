package ch.usi.dag.disl.util;

import ch.usi.dag.disl.CustomCodeElements.FutureLabelTarget;
import ch.usi.dag.disl.TestUtils;
import ch.usi.dag.disl.util.ClassFileAnalyzer.BasicValue;
import ch.usi.dag.disl.util.ClassFileAnalyzer.Frame;
import ch.usi.dag.disl.util.ClassFileAnalyzer.SourceValue;
import org.junit.Assert;
import org.junit.Test;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Map;

public class ClassFileFrameHelperTest {

    public static class TestClass {
        public int a(int a, int b, int c) {
            if (a > b) {
                return a + c;
            }
            if (c == 0) {
                return a + 10;
            }
            return a + b + c + 1;
        }
    }

    ClassModel testClass = TestUtils.__loadClass(TestClass.class);

    @Test
    public void createSourceMappingTest() {
        ClassDesc owner = testClass.thisClass().asSymbol();
        MethodModelCopy method = TestUtils.__getMethod(testClass, "a");
        Map<CodeElement, Frame<SourceValue>> sourceMapping = ClassFileFrameHelper.createSourceMapping(owner, method);
        Assert.assertFalse(sourceMapping.isEmpty());
        for (CodeElement element: method.instructions()) {
            Assert.assertTrue(sourceMapping.containsKey(element));
        }
    }


    @Test
    public void createBasicMappingTest() {
        ClassDesc owner = testClass.thisClass().asSymbol();
        MethodModelCopy method = TestUtils.__getMethod(testClass, "a");
        Map<CodeElement, Frame<BasicValue>> basicMapping = ClassFileFrameHelper.createBasicMapping(owner, method);
        Assert.assertFalse(basicMapping.isEmpty());
        for (CodeElement element: method.instructions()) {
            Assert.assertTrue(basicMapping.containsKey(element));
        }
    }

    @Test
    public void testFutureLabelBasicMapping() {
        ClassDesc owner = testClass.thisClass().asSymbol();
        MethodModelCopy method = TestUtils.__getMethod(testClass, "a");

        List<CodeElement> changedInstructions = method.instructions.stream().map(
                codeElement -> {
                    if (codeElement instanceof LabelTarget labelTarget) {
                        return new FutureLabelTarget(labelTarget.label());
                    }
                    return codeElement;
                }
        ).toList();

        Map<CodeElement, Frame<BasicValue>> basicMapping = ClassFileFrameHelper.createMapping(
                ClassFileFrameHelper.getBasicAnalyzer(),
                owner,
                changedInstructions,
                method.exceptionHandlers,
                method.methodTypeSymbol,
                method.flags
        );

        Assert.assertFalse(basicMapping.isEmpty());
        Assert.assertEquals(basicMapping.size(), changedInstructions.size());
        for (CodeElement element: changedInstructions) {
            Assert.assertTrue(basicMapping.containsKey(element));
        }
    }

    @Test
    public void testFutureLabelSourceMapping() {
        ClassDesc owner = testClass.thisClass().asSymbol();
        MethodModelCopy method = TestUtils.__getMethod(testClass, "a");

        List<CodeElement> changedInstructions = method.instructions.stream().map(
                codeElement -> {
                    if (codeElement instanceof LabelTarget labelTarget) {
                        return new FutureLabelTarget(labelTarget.label());
                    }
                    return codeElement;
                }
        ).toList();

        Map<CodeElement, Frame<SourceValue>> basicMapping = ClassFileFrameHelper.createMapping(
                ClassFileFrameHelper.getSourceAnalyzer(),
                owner,
                changedInstructions,
                method.exceptionHandlers,
                method.methodTypeSymbol,
                method.flags
        );

        Assert.assertFalse(basicMapping.isEmpty());
        Assert.assertEquals(basicMapping.size(), changedInstructions.size());
        for (CodeElement element: changedInstructions) {
            Assert.assertTrue(basicMapping.containsKey(element));
        }
    }
}
