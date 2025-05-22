package ch.usi.dag.disl.staticcontext;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.cfgCF.BasicBlockCF;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * <b>NOTE: This class is work in progress</b>
 * <br>
 * <br>
 * Provides static context information about instrumented instruction.
 */
public class LoopStaticContext extends BasicBlockStaticContext {

    private Map<BasicBlockCF, Set<BasicBlockCF>> dominatormapping;

    @Override
    protected ControlFlowGraph createControlFlowGraph(final MethodModelCopy method) {

        ControlFlowGraph cfg = ControlFlowGraph.build(method.instructions(), method.exceptionHandlers());

        dominatormapping = new HashMap<BasicBlockCF, Set<BasicBlockCF>>();

        Set<BasicBlockCF> entries = new HashSet<>();
        entries.add(cfg.getBasicBlock(method.instructions().getFirst()));

        Map<Label, CodeElement> labelTargetMap = ClassFileHelper.getLabelTargetMap(method.instructions());

        for (ExceptionCatch tcb : method.exceptionHandlers()) {
            entries.add(cfg.getBasicBlock(labelTargetMap.get(tcb.handler())));
        }

        for (BasicBlockCF bb : cfg.getNodes()) {

            Set<BasicBlockCF> dominators = new HashSet<>();

            if (entries.contains(bb)) {
                dominators.add(bb);
            } else {
                dominators.addAll(cfg.getNodes());
            }

            dominatormapping.put(bb, dominators);
        }

        // whether the dominators of any basic block is changed
        boolean changed;

        // loop until no more changes
        do {
            changed = false;

            for (BasicBlockCF bb : cfg.getNodes()) {

                if (entries.contains(bb)) {
                    continue;
                }

                Set<BasicBlockCF> dominators = dominatormapping.get(bb);
                dominators.remove(bb);

                // update the dominators of current basic block,
                // contains only the dominators of its predecessors
                for (BasicBlockCF predecessor : bb.getPredecessor()) {

                    if (dominators.retainAll(dominatormapping.get(predecessor))) {
                        changed = true;
                    }
                }

                dominators.add(bb);
            }
        } while (changed);

        return cfg;
    }

    /**
     * Returns true if the instrumented instruction is start of a loop.
     */
    public boolean isFirstOfLoop() {

        BasicBlockCF entry = _getMethodCfg ().getBasicBlock(staticContextData.getRegionStart());

        for (BasicBlockCF bb : entry.getPredecessor()) {
            if (dominatormapping.get(bb).contains(entry)) {
                return true;
            }
        }

        return false;
    }

}
