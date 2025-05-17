package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;
import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;


/**
 * Marks a method body. This marker can be safely used with constructors.
 * <p>
 * For normal methods, the {@link Before} snippets will be inserted before the
 * first instruction of a method. However, for constructors, they will be
 * inserted before the first instruction following a call to the superclass
 * constructor.
 */
// TODO LB: I believe this should be the default BodyMarker
// TODO LB: Consequently, current BodyMarker should be RawBodyMarker
public class AfterInitBodyMarker extends AbstractMarker {

    @Override
    public List<MarkedRegion> mark(final MethodModelCopy methodModel) {
        final MarkedRegion region = new MarkedRegion(__findBodyStart(methodModel));

        final List<CodeElement> instructions;
        if (methodModel.hasCode()) {
            instructions = methodModel.instructions();
        } else {
            instructions = new ArrayList<>();
        }

        // Add all RETURN instructions as marked-region ends.
        for (final CodeElement codeElement: instructions) {
            if (codeElement instanceof ReturnInstruction) {
                region.addEnd(codeElement);
            }
        }

        final WeavingRegion wr = region.computeDefaultWeavingRegion(methodModel);
        wr.setAfterThrowEnd(instructions.getLast());
        region.setWeavingRegion(wr);

        final List<MarkedRegion> result = new LinkedList<>();
        result.add(region);
        return result;
    }


    //
    // Finds the first instruction of a method body. For normal methods, this is
    // the first instruction of a method, but for constructor, this is the first
    // instruction after a call to the superclass constructor.
    private static CodeElement __findBodyStart(final MethodModelCopy methodModel) {

        if (!methodModel.hasCode()) {
            return null; // TODO should it throw an exception instad???
        }
        List<CodeElement> instructions = methodModel.instructions();

        if (!JavaNames.isConstructorName(methodModel.methodName().stringValue())) {
            return instructions.getFirst();
        }

        // if is a constructor we skip the super(...), so we exclude all instruction until after the first invoke special TODO is this correct???

        boolean methodStarted = false;

        for (int i = 0; i < instructions.size(); i++) {
            if (methodStarted) {
                return instructions.get(i);
            }
            CodeElement current = instructions.get(i);
            if (current instanceof InvokeInstruction && ((InvokeInstruction) current).opcode() == Opcode.INVOKESPECIAL) {
                methodStarted = true;
            }
        }

        return null; // TODO also here should it throw???
    }

}
