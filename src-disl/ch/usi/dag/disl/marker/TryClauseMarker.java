package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;


/**
 * Marks a try block.
 * <p>
 * Sets the start at the beginning of a try block and the end at the end of a
 * try block.
 */
public class TryClauseMarker extends AbstractDWRMarker {


    @Override
    public List<MarkedRegion> markWithDefaultWeavingReg(MethodModelCopy methodModel) {
        List<MarkedRegion> regions = new LinkedList<>();

        if (!methodModel.hasCode()) {
            return regions;
        }

        List<CodeElement> instructions = methodModel.instructions();

        Map<Label, CodeElement> labelTargetMap = ClassFileHelper.getLabelTargetMap(instructions);

        for (ExceptionCatch exceptionCatch: methodModel.exceptionHandlers()) {
            Label startLabel = exceptionCatch.tryStart();
            Label endLabel = exceptionCatch.tryEnd();

            CodeElement startTarget = labelTargetMap.get(startLabel);
            CodeElement endTarget = labelTargetMap.get(endLabel);
            if (startTarget == null || endTarget == null) {
                continue; //TODO should throw an exception???
            }

            CodeElement start = ClassFileHelper.firstNextRealInstruction(instructions, startTarget);
            // RFC LB: Consider nextRealInsn, since TCB end is exclusive
            // This depends on the semantics of marked region
            // TODO confirm this
            // even if the label is before the last instruction we take the instruction before since TCB end is exclusive
            CodeElement end = ClassFileHelper.firstPreviousRealInstruction(instructions, endTarget);
            regions.add(new MarkedRegion(start, end));
        }

        return regions;
    }

}
