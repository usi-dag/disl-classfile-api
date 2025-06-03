package ch.usi.dag.disl;

import ch.usi.dag.disl.coderep.ClassFileCodeTransformer;
import ch.usi.dag.disl.util.MethodModelCopy;
import org.junit.Assert;
import org.junit.Test;
import ch.usi.dag.disl.test.suite.bypassmodulevisibility.instr.DiSLClass;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.util.ArrayList;
import java.util.List;

public class SnippetUnprocessedCodeTest {

    @Test
    public void transformersTest() {
        Class<?> diSLClassClass = DiSLClass.class;
        ClassModel classModel = TestUtils.__loadClass(diSLClassClass);

        MethodModelCopy beforeEvent = TestUtils.__getMethod(classModel, "beforeEvent");
        MethodModelCopy afterEvent = TestUtils.__getMethod(classModel, "afterEvent");

        MethodTransform shift = ClassFileCodeTransformer.shiftLocalVarSlotCodeTransformer(0);
        MethodTransform bypass = ClassFileCodeTransformer.insertDynamicBypassControlCodeTransformer();

        MethodTransform exceptionHandlerBefore = ClassFileCodeTransformer.InsertExceptionHandlerCodeTransformer("snippet ch.usi.dag.disl.test.suite.bypassmodulevisibility.instr.DiSLClass.beforeEvent");
        MethodTransform exceptionHandlerAfter = ClassFileCodeTransformer.InsertExceptionHandlerCodeTransformer("snippet ch.usi.dag.disl.test.suite.bypassmodulevisibility.instr.DiSLClass.afterEvent");

        List<MethodTransform> transformersBeforeEvent = new ArrayList<>();
        List<MethodTransform> transformersAfterEvent = new ArrayList<>();

        transformersBeforeEvent.add(shift);
        transformersBeforeEvent.add(bypass);
        transformersBeforeEvent.add(exceptionHandlerBefore);

        transformersAfterEvent.add(shift);
        transformersAfterEvent.add(bypass);
        transformersAfterEvent.add(exceptionHandlerAfter);

        MethodModel transformedMethodBefore = ClassFileCodeTransformer.applyTransformers(beforeEvent, transformersBeforeEvent);
        MethodModel transformedMethodAfter = ClassFileCodeTransformer.applyTransformers(afterEvent, transformersAfterEvent);

        Assert.assertTrue(transformedMethodBefore.code().isPresent());
        Assert.assertTrue(transformedMethodAfter.code().isPresent());

    }
}
