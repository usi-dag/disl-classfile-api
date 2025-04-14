package ch.usi.dag.disl.marker;

import ch.usi.dag.disl.util.MethodModelCopy;

import java.util.LinkedList;
import java.util.List;


/**
 * Marker does not create any marking.
 */
public class EmptyMarker extends AbstractMarker {

    @Override
    public List<MarkedRegion> mark(final MethodModelCopy method) {
        return new LinkedList<>();
    }
}
