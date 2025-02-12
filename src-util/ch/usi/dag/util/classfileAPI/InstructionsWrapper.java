package ch.usi.dag.util.classfileAPI;

import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;
import java.util.ArrayList;
import java.util.List;

public class InstructionsWrapper {
    // this is a possible wrapper for the classfile instruction (ClassElement) since they do not have all methods of asm

    private final List<CodeElement> codeElementList;

    public InstructionsWrapper(List<CodeElement> codeElementList) {
        this.codeElementList = codeElementList;
    }

    public InstructionsWrapper(MethodModel methodModel) {
        if (methodModel.code().isEmpty()) {
            this.codeElementList = new ArrayList<>();
        } else {
            this.codeElementList = methodModel.code().get().elementList();
        }
    }

    public int size() {
        return codeElementList.size();
    }

    public boolean isEmpty() {
        return codeElementList.isEmpty();
    }

    public InstructionWrapper getFirst() {
        if (codeElementList.isEmpty()) {
            return null;
        }
        return new InstructionWrapper(codeElementList.getFirst(), codeElementList, 0);
    }

    public InstructionWrapper getLast() {
        if (codeElementList.isEmpty()) {
            return null;
        }
        return new InstructionWrapper(codeElementList.getLast(), codeElementList, codeElementList.size() -1);
    }

    public InstructionWrapper get(int index) {
        return new InstructionWrapper(codeElementList.get(index), codeElementList, index);
    }

    public class InstructionWrapper {
        private final CodeElement codeElement;
        private final List<CodeElement> codeElementList;
        private final int index;

        public InstructionWrapper(CodeElement codeElement, List<CodeElement> codeElementList, int index) {
            this.codeElementList = codeElementList;
            this.codeElement = codeElement;
            this.index = index;
        }

        public CodeElement getCodeElement() {
            return codeElement;
        }

        public InstructionWrapper getNext() {
            if (index + 1 >= codeElementList.size()) {
                return null;  // TODO should throw an exception instead????
            }
            return new InstructionWrapper(codeElementList.get(index+1), codeElementList, index+1);
        }

        public InstructionWrapper getPrevious() {
            if (index -1 < 0) {
                return null;  // TODO should throw an exception instead????
            }
            return new InstructionWrapper(codeElementList.get(index-1), codeElementList, index-1);
        }
    }
}
