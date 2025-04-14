package ch.usi.dag.disl.staticcontext;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.ref.WeakReference;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.cfgCF.BasicBlockCF;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;
import ch.usi.dag.disl.util.Logging;
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

    protected ControlFlowGraph createControlFlowGraph (final MethodModelCopy method) {
        return new ControlFlowGraph(method);
    }

    //
    // Some classes may be loaded by multiple class loaders.  Until we support
    // this properly, we just cache the last CFG associated with a concrete
    // MethodNode instance. This should be enough, because we don't return to
    // CFG of methods that have already been instrumented.
    //

    private record Memo(MethodModelCopy method, ControlFlowGraph cfg) {
    }

    private WeakReference <Memo> __memoReference = new WeakReference <> (null);


    protected final ControlFlowGraph _getMethodCfg () {
        final Memo memo = __memoReference.get ();
        final MethodModelCopy method = staticContextData.getMethodModel();

        if (memo != null && memo.method == method) {
            return memo.cfg;

        } else {
            final ControlFlowGraph result = createControlFlowGraph (method);
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
                staticContextData.getClassModel().thisClass().name(),
                staticContextData.getMethodModel().methodName(),
                staticContextData.getMethodModel().methodTypeSymbol().descriptorString()
            );

            return 0;
        }
    }


    private int __getSize (final int index) {
        //
        // If the start instruction is also an end instruction,
        // then the size of the basic block is 1 instruction.
        //
        final ControlFlowGraph controlFlowGraph = _getMethodCfg();
        final BasicBlockCF bb = controlFlowGraph.getNodes().get(index);


        CodeElement insn = bb.getEntry();
        final CodeElement exit = bb.getExit();

        int result = 1;
        while (insn != exit) {
            result += (insn instanceof Instruction) ? 1 : 0;
            insn = ClassFileHelper.nextInstruction(controlFlowGraph.getInstructions(), insn);
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
