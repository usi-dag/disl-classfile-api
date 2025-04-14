package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.util.LinkedList;
import java.util.List;

import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;
import ch.usi.dag.disl.util.MethodModelCopy;


abstract class AbstractInsnMarker extends AbstractMarker {


    public abstract List<CodeElement> markInstruction(MethodModelCopy methodModel);

    @Override
    public final List<MarkedRegion> mark(final MethodModelCopy methodModel) {
        final List<MarkedRegion> regions = new LinkedList<>();

        for (final CodeElement instruction: markInstruction(methodModel)) {
            final MarkedRegion region = new MarkedRegion(instruction, instruction);
            region.setWeavingRegion(new WeavingRegion(
                    instruction, new LinkedList<>(region.getEnds()),
                    instruction, instruction
            ));
            regions.add(region);
        }
        return regions;
    }

}
