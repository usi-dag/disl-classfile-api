package ch.usi.dag.disl.marker;

import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.LinkedList;
import java.util.List;

import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;

/**
 * Marks object creation.
 * <p>
 * Sets the start before new instruction and the end after the constructor
 * invocation.
 * <p>
 * <b>Note:</b> This class is work in progress.
 */
public class NewObjMarker extends AbstractDWRMarker {

    // NOTE: does not work for arrays
    @Override
    public List<MarkedRegion> markWithDefaultWeavingReg(MethodModelCopy methodModel) {
        List<MarkedRegion> regions = new LinkedList<>();
        int invokedNews = 0;

        if (!methodModel.hasCode()) {
            return regions;
        }

        // find invocation of constructor after new instruction
        List<CodeElement> instructions = methodModel.instructions();
        for (CodeElement codeElement: instructions) {
            if (!(codeElement instanceof Instruction instruction)) {
                continue;
            }
            // track new instruction
            if (instruction.opcode() == Opcode.NEW) {
                ++invokedNews;
            }
            // if it is invokespecial and there are new pending
            if (instruction.opcode() == Opcode.INVOKESPECIAL && invokedNews > 0) {
                InvokeInstruction invokeInstruction = (InvokeInstruction) instruction;

                if (JavaNames.isConstructorName(invokeInstruction.name().stringValue())) {
                    regions.add(new MarkedRegion(codeElement, codeElement));
                }
            }
        }

        return regions;
    }

}
