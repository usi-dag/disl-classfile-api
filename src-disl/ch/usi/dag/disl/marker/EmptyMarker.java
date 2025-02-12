package ch.usi.dag.disl.marker;

import java.lang.classfile.MethodModel;
import java.util.LinkedList;
import java.util.List;

import org.objectweb.asm.tree.MethodNode;


/**
 * Marker does not create any marking.
 */
public class EmptyMarker extends AbstractMarker {

    @Override
    public List <MarkedRegion> mark (final MethodNode method) {
        return new LinkedList <MarkedRegion> ();
    }

    @Override
    public List<MarkedRegion> mark(final MethodModel method) {
        return new LinkedList<>();
    }
}
