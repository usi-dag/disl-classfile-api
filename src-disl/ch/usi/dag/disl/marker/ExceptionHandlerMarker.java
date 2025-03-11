package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;

/**
 * Marks an exception handler.
 * <p>
 * Sets the start at the beginning of an exception handler and the end at the
 * end of an exception handler.
 */
public class ExceptionHandlerMarker extends AbstractDWRMarker {


    @Override
    public List<MarkedRegion> markWithDefaultWeavingReg(MethodModel methodModel) {
        List<MarkedRegion> regions = new LinkedList<>();

        if (methodModel.code().isEmpty()) {
            return regions;
        }

        CodeModel code = methodModel.code().get();
        List<CodeElement> instructions = code.elementList();
        List<ExceptionCatch> exceptions = code.exceptionHandlers();

        ControlFlowGraph cfg = new ControlFlowGraph(methodModel);

        cfg.visit(instructions.getFirst());

        Map<Label, LabelTarget> labelTargetMap = ClassFileHelper.getLabelTargetMap(instructions);

        for (ExceptionCatch exceptionCatch: exceptions) {
            LabelTarget handler = labelTargetMap.get(exceptionCatch.handler());
            if (handler == null) {
                continue; // TODO should throw???
            }
            List<CodeElement> exits = cfg.visit(handler);
            regions.add(new MarkedRegion(
                    ClassFileHelper.firstNextRealInstruction(instructions, handler), exits
            ));
        }

        return regions;
    }

}
