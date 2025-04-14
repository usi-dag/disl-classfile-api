package ch.usi.dag.disl.marker;

import ch.usi.dag.disl.util.MethodModelCopy;
import java.util.List;

/**
 * AbstractDWRMarker is an enhancement of AbstractMarker automatically computing
 * weaving region. This includes correct position of end region (not after jump)
 * and meaningful try block.
 *
 * <p>
 * User has to implement markWithDefaultWeavingReg method.
 */
public abstract class AbstractDWRMarker extends AbstractMarker {


    public final List<MarkedRegion> mark(MethodModelCopy methodModel) {
        List<MarkedRegion> markedRegions = markWithDefaultWeavingReg(methodModel);

        for (MarkedRegion markedRegion: markedRegions) {
            markedRegion.setWeavingRegion(markedRegion.computeDefaultWeavingRegion(methodModel));
        }
        return markedRegions;
    }

    /**
     * Implementation of this method should return list of marked regions with
     * filled start and end of the region.
     *
     * <p>
     * The regions will get automatic after throw computation.
     * <p>
     * The regions will get automatic branch skipping at the end.
     */
    public abstract List<MarkedRegion> markWithDefaultWeavingReg(MethodModelCopy methodModel);
}
