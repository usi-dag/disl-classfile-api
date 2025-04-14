package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.util.LinkedList;
import java.util.List;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.cfgCF.BasicBlockCalculator;

/**
 * Marks a basic block.
 * <p>
 * Sets the start at the beginning of a basic block and the end at the end of a
 * basic block. Considers only jump instructions, lookup switch and table
 * switch.
 */
public class BasicBlockMarker extends AbstractDWRMarker {

    protected boolean isPrecise = false;


    @Override
    public List<MarkedRegion> markWithDefaultWeavingReg(final MethodModelCopy methodModel) {
        final List<MarkedRegion> regions = new LinkedList<>();

        if (!methodModel.hasCode()) {
            return regions;
        }
        final List<CodeElement> instructions = methodModel.instructions();

        final List<CodeElement> separators = BasicBlockCalculator.getAll(instructions, methodModel.exceptionHandlers(), isPrecise);

        final Instruction last = ClassFileHelper.selectReal(instructions).getLast();

        separators.add(last);

        for (int i = 0; i < separators.size() - 1; i ++) {
            final CodeElement start = separators.get(i);
            CodeElement end = separators.get(i + 1);

            if (i != separators.size() - 2) {
                end = ClassFileHelper.previousInstruction(instructions, end);
            }

            regions.add(new MarkedRegion(
                    start, ClassFileHelper.firstPreviousRealInstruction(instructions, end)
            ));
        }

        return regions;
    }

}
