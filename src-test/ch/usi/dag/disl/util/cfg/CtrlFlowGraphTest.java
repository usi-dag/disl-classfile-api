package ch.usi.dag.disl.util.cfg;

import java.io.IOException;
import java.util.function.Predicate;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import ch.usi.dag.disl.util.Insn;
import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.util.asm.ClassNodeHelper;


public class CtrlFlowGraphTest {

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

    //

    private static final Predicate <MethodNode> __isDefaultCtor__ = mn -> {
        return JavaNames.isConstructorName (mn.name) && "()V".equals (mn.desc);
    };

    @Test
    public void objectCtorHasOneBasicBlock () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (Object.class, __isDefaultCtor__);
        Assert.assertEquals (1, cfg.getNodes ().size ());
    }


    @Test
    public void objectCtorBasicBlockHasOneInsn () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (Object.class, __isDefaultCtor__);
        final BasicBlock bb = cfg.getNodes ().get (0);
        Assert.assertEquals (1, __getBasicBlockSize (bb));
    }


    @Test
    public void emptyMethodHasOneBasicBlock () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "empty");
        Assert.assertEquals (1, cfg.getNodes ().size ());
    }


    @Test
    public void emptyMethodBasicBlockHasOneInsn () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "empty");
        final BasicBlock bb = cfg.getNodes ().get (0);
        Assert.assertEquals (1, __getBasicBlockSize (bb));
    }


    @Test
    public void threeInvocationsHasOneBasicBlock () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "threeInvocations");
        Assert.assertEquals (1, cfg.getNodes ().size ());
    }


    @Test
    public void ifThenBranchHasThreeBasicBlocks () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "ifThenBranch");
        Assert.assertEquals (3, cfg.getNodes ().size ());
    }


    @Test
    public void ifThenElseBranchHasFourBasicBlocks () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "ifThenElseBranch");
        Assert.assertEquals (4, cfg.getNodes ().size ());
    }


    @Test
    public void ifThenReturnElseReturnHasThreeBasicBlocks () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "ifThenReturnElseReturn");
        Assert.assertEquals (3, cfg.getNodes ().size ());
    }


    @Test
    public void simpleForLoopHasFourBasicBlocks () throws IOException {
        final CtrlFlowGraph cfg = __createCfg (A.class, "simpleForLoop");
        Assert.assertEquals (4, cfg.getNodes ().size ());
    }

    //

    private CtrlFlowGraph __createCfg (
        final Class <?> owner, final String name
    ) throws IOException {
        return __createCfg (__findMethod (
            __loadClass (owner), m -> name.equals (m.name)
        ));
    }


    private CtrlFlowGraph __createCfg (
        final Class <?> owner, final Predicate <? super MethodNode> filter
    ) throws IOException {
        return __createCfg (__findMethod (__loadClass (owner), filter));
    }


    private CtrlFlowGraph __createCfg (final MethodNode mn) {
        return CtrlFlowGraph.build (mn);
    }

    //

    private ClassNode __loadClass (final Class <?> cls) throws IOException {
        return ClassNodeHelper.FULL.load (cls.getName ());
    }


    private MethodNode __findMethod (
        final ClassNode owner, final Predicate <? super MethodNode> filter
    ) {
        return owner.methods.stream ().filter (filter).findFirst ().get ();
    }


    //

    private int __getBasicBlockSize (final BasicBlock bb) {
        //
        // If the start instruction is also an end instruction,
        // then the size of the basic block is 1 instruction.
        //
        AbstractInsnNode insn = bb.getEntryNode ();
        final AbstractInsnNode exit = bb.getExitNode ();

        int result = 1;
        while (insn != exit) {
            result += Insn.isVirtual (insn) ? 0 : 1;
            insn = insn.getNext ();
        }

        return result;
    }

}
