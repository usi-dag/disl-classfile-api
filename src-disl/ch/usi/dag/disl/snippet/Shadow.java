package ch.usi.dag.disl.snippet;

import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.util.List;

/**
 * Holds information about a region where a snippet will be woven. The shadow
 * contains two type of regions. A logical region, which is available directly
 * in the shadow, is designed mainly for static analysis and represents a region
 * of bytecode which is logically captured by a marker. In contrast, a weaving
 * region is designed as a guidance for the weaver, and indicates where exactly
 * should the code be woven.
 */
public final class Shadow {
    protected final ClassModel classModel;
    protected final MethodModelCopy methodModel;

    protected final Snippet snippet;

    private final CodeElement regionStart;
    private final List<CodeElement> regionEnds;

    private final WeavingRegion weavingRegion;

    /**
     * Holds exact information where the code will be woven. This structure is
     * a guiding source for the weaver.
     */
    public static class WeavingRegion {
        // NOTE: "ends" can be null. This means, that we have the special case
        // where we need to generate before and after snippets on the same
        // position.
        // This is for example case of putting snippets before and after
        // region that includes only return instruction.
        // In this case, after has to be generated also before the return
        // instruction otherwise is never invoked.
        // "ends" containing null notifies the weaver about this situation.

        private CodeElement start;
        private List<CodeElement> ends;
        private CodeElement afterThrowStart;
        private CodeElement afterThrowEnd;

        public WeavingRegion(final CodeElement start, final List<CodeElement> ends,
                             final CodeElement afterThrowStart, final CodeElement afterThrowEnd) {
            super();
            this.start = start;
            this.ends = ends;
            this.afterThrowStart = afterThrowStart;
            this.afterThrowEnd = afterThrowEnd;
        }

        public CodeElement getStart() {return start;}
        public List<CodeElement> getEnds() {return ends;}
        public CodeElement getAfterThrowStart() {return afterThrowStart;}
        public CodeElement getAfterThrowEnd() {return afterThrowEnd;}

        public void setStart(final CodeElement start) {this.start = start;}
        public void setEnds(final List<CodeElement> ends) {this.ends = ends;}
        public void setAfterThrowStart(CodeElement afterThrowStart) {this.afterThrowStart = afterThrowStart;}
        public void setAfterThrowEnd(CodeElement afterThrowEnd) {this.afterThrowEnd = afterThrowEnd;}
    }

    public Shadow(final ClassModel classModel, final MethodModelCopy methodModel, final Snippet snippet,
                  final CodeElement regionStart, final List<CodeElement> regionEnds, final WeavingRegion weavingRegion) {
        super();
        this.classModel = classModel;
        this.methodModel = methodModel;
        this.snippet = snippet;
        this.regionStart = regionStart;
        this.regionEnds = regionEnds;
        this.weavingRegion = weavingRegion;
    }
    // special copy constructor for caching support
    public Shadow(final Shadow that) {
        this.classModel = that.classModel;
        this.methodModel = that.methodModel;
        this.snippet = that.snippet;
        this.regionStart = that.regionStart;
        this.regionEnds = that.regionEnds;
        this.weavingRegion = that.weavingRegion;
    }

    public ClassModel getClassModel() {return classModel;}
    public MethodModelCopy getMethodModel() {return methodModel;}
    public CodeElement getRegionStart() {return regionStart;}
    public List<CodeElement> getRegionEnds() {return regionEnds;}
    public WeavingRegion getWeavingRegion() {return weavingRegion;}
}
