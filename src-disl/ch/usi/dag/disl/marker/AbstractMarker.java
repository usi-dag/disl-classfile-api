package ch.usi.dag.disl.marker;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.usi.dag.disl.util.MethodModelCopy;
import ch.usi.dag.disl.util.ClassFileAPI.InstructionsWrapper;
import ch.usi.dag.disl.exception.MarkerException;
import ch.usi.dag.disl.snippet.Shadow;
import ch.usi.dag.disl.snippet.Shadow.WeavingRegion;
import ch.usi.dag.disl.snippet.Snippet;


/**
 * Simplifies {@link Marker} implementation by providing a
 * {@link #mark(MethodModelCopy)} method that returns a list of {@link MarkedRegion}
 * instances instead of {@link Shadow} instances. The {@link MarkedRegion} class
 * itself supports automatic computation of weaving region based on simplified
 * region specification.
 */
public abstract class AbstractMarker implements Marker {

    /**
     * values where the weaving region can be precomputed by
     * computeDefaultWeavingRegion method.
     */
    public static class MarkedRegion {

        private CodeElement start;
        private final List <CodeElement> ends;

        private WeavingRegion weavingRegion;


        /**
         * Returns region start.
         */
        public CodeElement getStart () {
            return start;
        }


        /**
         * Set region start.
         */
        public void setStart (final CodeElement start) {
            this.start = start;
        }


        /**
         * Returns the list of region ends.
         */
        public List <CodeElement> getEnds () {
            return ends;
        }


        /**
         * Appends a region to the list of region ends.
         */
        public void addEnd (final CodeElement exitpoint) {
            this.ends.add (exitpoint);
        }


        /**
         * Returns the weaving region.
         */
        public WeavingRegion getWeavingRegion () {
            return weavingRegion;
        }


        /**
         * Sets the weaving region.
         */
        public void setWeavingRegion (final WeavingRegion weavingRegion) {
            this.weavingRegion = weavingRegion;
        }


        /**
         * Creates a {@link MarkedRegion} with start.
         */
        public MarkedRegion (final CodeElement start) {
            this.start = start;
            this.ends = new LinkedList<>();
        }


        /**
         * Creates a {@link MarkedRegion} with start and a single end.
         */
        public MarkedRegion (
            final CodeElement start, final CodeElement end
        ) {
            this.start = start;
            this.ends = new LinkedList<>();
            this.ends.add (end);
        }


        /**
         * Creates a {@link MarkedRegion} with start and a list of ends.
         */
        public MarkedRegion (
            final CodeElement start, final List <CodeElement> ends
        ) {
            this.start = start;
            this.ends = ends;
        }


        /**
         * Creates a {@link MarkedRegion} with start, multiple ends, and a
         * weaving region.
         */
        public MarkedRegion (final CodeElement start,
            final List <CodeElement> ends, final WeavingRegion weavingRegion
        ) {
            this.start = start;
            this.ends = ends;
            this.weavingRegion = weavingRegion;
        }


        /**
         * Test if all required fields are filled
         */
        public boolean valid () {
            return start != null && ends != null && weavingRegion != null;
        }


        /**
         * Computes the default {@link WeavingRegion} for this
         * {@link MarkedRegion}. The computed {@link WeavingRegion} instance
         * will NOT be automatically associated with this {@link MarkedRegion}.
         */
        public WeavingRegion computeDefaultWeavingRegion(final MethodModelCopy methodModel) {
            final CodeElement wStart = start;
            final CodeElement afterThrowStart = start;
            InstructionsWrapper.InstructionWrapper afterThrowEndWrapper = null;
            CodeElement afterThrowEnd = null;

            final Set<CodeElement> endsSet = new HashSet<>(ends);
            // here I created a wrapper to simulate like if it was asm
            InstructionsWrapper instructionsWrapper = new InstructionsWrapper(methodModel);
            InstructionsWrapper.InstructionWrapper instr = instructionsWrapper.getLast();
            while (instr != null) {
                if (endsSet.contains(instr.getCodeElement())) {
                    afterThrowEnd = instr.getCodeElement();
                    afterThrowEndWrapper = instr;
                    break;
                }
                instr = instr.getPrevious();
            }
            // TODO is this equivalent to the functionality of the asm function??????
            if (afterThrowEnd instanceof LabelTarget) {
                final Set<Label> tcb_ends = new HashSet<>();
                if (methodModel.hasCode()) {
                    for (ExceptionCatch exceptionCatch: methodModel.exceptionHandlers()) {
                        tcb_ends.add(exceptionCatch.tryEnd());
                    }
                }

                while (afterThrowEnd instanceof LabelTarget &&
                        tcb_ends.contains(((LabelTarget) afterThrowEnd).label())    // a LabelTarget contain a Label, this is why the check and cast are needed
                ) {
                    afterThrowEndWrapper = afterThrowEndWrapper.getPrevious();
                    afterThrowEnd = afterThrowEndWrapper.getCodeElement();
                }
            }

            return new WeavingRegion(wStart, null, afterThrowStart, afterThrowEnd);
        }


    }

    @Override
    public List<Shadow> mark(final ClassModel classModel, final MethodModelCopy methodModel, Snippet snippet) throws MarkerException {
        final List<MarkedRegion> markedRegions = mark(methodModel);
        final List<Shadow> result = new LinkedList<>();

        for (final MarkedRegion markedRegion: markedRegions) {
            if (!markedRegion.valid()) {
                throw new MarkerException("Marker " + this.getClass()
                        + " produced invalid MarkedRegion (some MarkedRegion" +
                        " fields where not set)");
            }
            result.add(
                    new Shadow(classModel, methodModel, snippet, markedRegion.getStart(), markedRegion.getEnds(), markedRegion.getWeavingRegion())
            );
        }

        return result;
    }


    /**
     * Implementation of this method should return list of {@link MarkedRegion}
     * instances with start, ends, and the weaving region filled.
     *
     * @param methodModel
     *        method node of the marked class
     * @return returns list of MarkedRegion
     */
    public abstract List<MarkedRegion> mark(MethodModelCopy methodModel);
}
