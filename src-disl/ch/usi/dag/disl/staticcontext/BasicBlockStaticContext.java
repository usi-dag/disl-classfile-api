package ch.usi.dag.disl.staticcontext;

import java.lang.ref.WeakReference;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;

import ch.usi.dag.disl.util.Insn;
import ch.usi.dag.disl.util.Logging;
import ch.usi.dag.disl.util.cfg.BasicBlock;
import ch.usi.dag.disl.util.cfg.CtrlFlowGraph;
import ch.usi.dag.util.logging.Logger;


/**
 * Provides basic block related static context information for the method being
 * instrumented. method.
 * <p>
 * <b>Note:</b>This class is a work in progress, the API is being finalized.
 */
public class BasicBlockStaticContext extends AbstractStaticContext {

    private final Logger __log = Logging.getPackageInstance ();

    //

    protected CtrlFlowGraph createControlFlowGraph (final MethodNode method) {
        return new CtrlFlowGraph (method);
    }

    //
    // Some classes may be loaded by multiple class loaders.  Until we support
    // this properly, we just cache the last CFG associated with a concrete
    // MethodNode instance. This should be enough, because we don't return to
    // CFG of methods that have already been instrumented.
    //

    private static class Memo {
        final MethodNode method;
        final CtrlFlowGraph cfg;

        Memo (final MethodNode method, final CtrlFlowGraph cfg) {
            this.method = method;
            this.cfg = cfg;
        }
    }

    private WeakReference <Memo> __memoReference = new WeakReference <> (null);


    protected final CtrlFlowGraph _getMethodCfg () {
        final Memo memo = __memoReference.get ();
        final MethodNode method = staticContextData.getMethodNode ();

        if (memo != null && memo.method == method) {
            return memo.cfg;

        } else {
            final CtrlFlowGraph result = createControlFlowGraph (method);
            __memoReference = new WeakReference <> (new Memo (method, result));
            return result;
        }
    }


    /**
     * Returns total number of basic blocks in this method.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use the
     * {@link #getCount()} method instead.
     *
     * @return the number of basic blocks in this method.
     */
    @Deprecated
    public int getTotBBs () {
        return getCount ();
    }


    /**
     * Returns total number of basic blocks in this method.
     *
     * @return The number of basic blocks in this method, must be greater than
     *         zero, because every method should have at least one basic block
     *         represented by the return instruction.
     */
    public int getCount () {
        return _getMethodCfg ().getNodes ().size ();
    }


    /**
     * Calculates the size of this basic block in terms bytecode instructions.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use the
     * {@link #getSize()} method instead.
     *
     * @return size of this basic block.
     */
    @Deprecated
    public int getBBSize () {
        return getSize ();
    }


    /**
     * Calculates the size of this basic block in terms bytecode instructions.
     *
     * @return size of this basic block.
     */
    public int getSize () {
        final int index = getIndex ();
        if (index >= 0) {
            return __getSize (index);

        } else {
            __log.warn (
                "could not determine basic block index in %s.%s%s",
                staticContextData.getClassNode ().name,
                staticContextData.getMethodNode ().name,
                staticContextData.getMethodNode ().desc
            );

            return 0;
        }
    }


    private int __getSize (final int index) {
        //
        // If the start instruction is also an end instruction,
        // then the size of the basic block is 1 instruction.
        //
        final BasicBlock bb = _getMethodCfg ().getNodes ().get (index);

        AbstractInsnNode insn = bb.getEntryNode ();
        final AbstractInsnNode exit = bb.getExitNode ();

        int result = 1;
        while (insn != exit) {
            result += Insn.isVirtual (insn) ? 0 : 1;
            insn = insn.getNext ();
        }

        return result;
    }


    /**
     * Returns the index of this basic block within the instrumented method.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use the
     * {@link #getIndex()} method instead.
     *
     * @return index of this basic block within a method.
     */
    @Deprecated
    public int getBBindex () {
        return getIndex ();
    }


    /**
     * Returns the index of this basic block within the instrumented method.
     *
     * @return index of this basic block within a method, or -1 if the basic
     *         block index could not be found.
     */
    public int getIndex () {
        return _getMethodCfg ().getIndex (staticContextData.getRegionStart ());
    }

}
