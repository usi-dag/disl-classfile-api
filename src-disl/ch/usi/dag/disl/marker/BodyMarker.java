package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ReturnInstruction;
import java.util.LinkedList;
import java.util.List;

import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;


/**
 * Marks a method body.
 * <p>
 * Sets the start at the beginning of a method and the end at the end of a
 * method.
 */
public class BodyMarker extends AbstractMarker {


    @Override
    public List<MarkedRegion> mark(final MethodModel method) {
        final List<MarkedRegion> regions = new LinkedList<>();
        if (method.code().isEmpty()) {
            return regions; // TODO should I return an error maybe???
        }
        List<CodeElement> instructions = method.code().get().elementList();
        final MarkedRegion region = new MarkedRegion(instructions.getFirst());
        for (final CodeElement codeElement: instructions) {
            if (codeElement instanceof ReturnInstruction) {
                region.addEnd(codeElement);
            }
        }
        final WeavingRegion weavingRegion = region.computeDefaultWeavingRegion(method);
        weavingRegion.setAfterThrowEnd(instructions.getLast());
        region.setWeavingRegion(weavingRegion);
        regions.add(region);
        return regions;
    }

}
