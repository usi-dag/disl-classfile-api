package ch.usi.dag.disl.util.cfgCF;

import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.ClassFileAPI.ClassModelHelper;
import org.junit.Test;
import org.junit.Assert;

import java.io.IOException;
import java.lang.classfile.*;
import java.util.List;
import java.util.function.Predicate;

public class ControlFlowGraphTest {

    public static class A {

        public void empty () {
            // Basic block #1 (implicit return)
        }

        public void threeInvocations () {
            // Basic block #1 (including implicit return)
            empty ();
            empty ();
            empty ();
        }

        public void ifThenBranch () {
            // Basic block #1 (expression)
            if (Math.random () > 0.5) {
                // Basic block #2
                empty ();
            }

            // Basic block #3 (implicit return)
        }

        public void ifThenElseBranch () {
            // Basic block #1
            if (Math.random () > 0.5) {
                // Basic block #2
                empty ();
            } else {
                // Basic block #3
                empty ();
            }

            // Basic block #4 (implicit return)
        }


        public void ifThenReturnElseReturn () {
            // Basic block #1
            if (Math.random () > 0.5) {
                // Basic block #2
                return;
            } else {
                // Basic block #3
                return;
            }
        }


        public void simpleForLoop () {
            // Basic block #1
            for (int i = 0; i < 10 /* Basic block #2 (test) */; i++) {
                // Basic block #3
                empty ();
            }

            // Basic block #4 (implicit return)
        }
    }

    private static final Predicate<MethodModel> __isDefaultCtor__ = mm -> JavaNames.isConstructorName(mm.methodName().stringValue()) && mm.methodTypeSymbol().descriptorString().equals("()V");

    private ControlFlowGraph __createCFG(final Class<?> owner, final Predicate<? super MethodModel> filter) throws IOException {
        return __createCFG(__findMethod(__loadClass(owner), filter));
    }

    private ControlFlowGraph __createCFG(final MethodModel methodModel) {
        if (methodModel.code().isEmpty()) {
            throw new RuntimeException("Cannot create CFG, method " + methodModel.methodName() + " has no code");
        }
        MethodModelCopy copy = new MethodModelCopy(methodModel);

        List<CodeElement> copied = copy.instructions();
        return ControlFlowGraph.build(copied, copy.exceptionHandlers());
    }

    private ControlFlowGraph __createCFG(final Class<?> owner, final String name) throws IOException {
        return __createCFG(__findMethod(
                __loadClass(owner), mm -> name.equals(mm.methodName().stringValue())
        ));
    }

    private ClassModel __loadClass(final Class<?> cls) throws IOException {
        return ClassModelHelper.DEFAULT.load(cls.getName());
    }

    private MethodModel __findMethod(final ClassModel owner, final Predicate<? super MethodModel> filter) {
        return owner.methods().stream().filter(filter).findFirst().get();
    }

    private int __getBasicBlockSize(final BasicBlockCF bb) {
        CodeElement ins = bb.getEntry();
        final CodeElement exit = bb.getExit();
        int result = 1;
        while (ins != exit) {
            result += ins instanceof Instruction? 1: 0;
            ins = bb.getNextElement(ins);
        }
        return result;
    }

    @Test
    public void objectCtorHasOneBasicBlock() throws IOException {
        final ControlFlowGraph cfg = __createCFG(Object.class, __isDefaultCtor__);
        Assert.assertEquals(1, cfg.getNodes().size());
    }

    @Test
    public void objectCtorBasicBlockHasOneInsn () throws IOException {
        final ControlFlowGraph cfg = __createCFG(Object.class, __isDefaultCtor__);
        final BasicBlockCF bb = cfg.getNodes().get(0);
        Assert.assertEquals(1, __getBasicBlockSize(bb));
    }

    @Test
    public void emptyMethodHasOneBasicBlock () throws IOException {
        final ControlFlowGraph cfg = __createCFG (ControlFlowGraphTest.A.class, "empty");
        Assert.assertEquals (1, cfg.getNodes ().size ());
    }

    @Test
    public void emptyMethodBasicBlockHasOneInsn () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "empty");
        final BasicBlockCF bb = cfg.getNodes ().get (0);
        Assert.assertEquals (1, __getBasicBlockSize (bb));
    }

    @Test
    public void threeInvocationsHasOneBasicBlock () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "threeInvocations");
        Assert.assertEquals (1, cfg.getNodes ().size ());
    }

    @Test
    public void ifThenBranchHasThreeBasicBlocks () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "ifThenBranch");
        Assert.assertEquals (3, cfg.getNodes ().size ());
    }

    @Test
    public void ifThenElseBranchHasFourBasicBlocks () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "ifThenElseBranch");
        Assert.assertEquals (4, cfg.getNodes ().size ());
    }

    @Test
    public void ifThenReturnElseReturnHasThreeBasicBlocks () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "ifThenReturnElseReturn");
        Assert.assertEquals (3, cfg.getNodes ().size ());
    }

    @Test
    public void simpleForLoopHasFourBasicBlocks () throws IOException {
        final ControlFlowGraph cfg = __createCFG(ControlFlowGraphTest.A.class, "simpleForLoop");
        Assert.assertEquals (4, cfg.getNodes ().size ());
    }
}
