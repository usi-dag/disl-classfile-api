package ch.usi.dag.disl.util.cfgCF;

import ch.usi.dag.disl.util.ClassFileHelper;

import java.lang.classfile.CodeElement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BasicBlockCF implements Iterable<CodeElement> {
    private final List<CodeElement> instructions; // pointer to the complete list of instruction of the method used to navigate

    /** Index of this basic block within a method. */
    private final int index;

    /** The entry instruction of this basic block. */
    private final CodeElement entry;

    /** The exit instruction of this basic block. */
    private CodeElement exit;

    /** The set of predecessor basic blocks. */
    private final Set<BasicBlockCF> predecessor = new HashSet<>();

    /** The set of successor basic blocks. */
    private final Set<BasicBlockCF> successors = new HashSet<>();

    // joins refer to the join point of a new cfg to an existing cfg in the same method.
    // NOTE that an exception handler is regarded as a new cfg but not included in the normal execution cfg
    private final Set<BasicBlockCF> joints = new HashSet<>();

    public BasicBlockCF(final int index, final CodeElement entry, final CodeElement exit, List<CodeElement> instructions) {
        this.index = index;
        this.entry = entry;
        this.exit = exit;
        this.instructions = instructions;
    }

    public int getIndex() {
        return index;
    }

    // method used for tests
    public CodeElement getNextElement(CodeElement element) {
        return ClassFileHelper.nextInstruction(this.instructions, element);
    }

    public CodeElement getEntry() {
        return this.entry;
    }

    public CodeElement getExit() {
        return this.exit;
    }

    public void setExit(CodeElement newExit) {
        this.exit = newExit;
    }

    public Set<BasicBlockCF> getPredecessor() {
        return predecessor;
    }

    public Set<BasicBlockCF> getSuccessors() {
        return successors;
    }

    public Set<BasicBlockCF> getJoints() {
        return joints;
    }

    @Override
    public Iterator<CodeElement> iterator() {
        return new BasicBlockIterator();
    }

    private CodeElement getNext(CodeElement element) {
        return ClassFileHelper.nextInstruction(this.instructions, element);
    }


    class BasicBlockIterator implements Iterator<CodeElement> {
        private CodeElement current;

        public BasicBlockIterator() {
            current = entry;
        }

        @Override
        public boolean hasNext() {
            return current != getNext(exit);
        }

        @Override
        public CodeElement next() {
            final CodeElement result = current;
            current = getNext(current);
            return result;
        }

        @Override
        public void remove () {
            throw new RuntimeException("BasicBlockIterator is a Readonly iterator.");
        }

    }
}
