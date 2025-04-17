package ch.usi.dag.disl.util.ClassFileAPI;

import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.LineNumber;
import java.util.ArrayList;
import java.util.List;

public class InstructionsWrapper {
    // this is a possible wrapper for the classfile instruction (ClassElement) since they do not have all methods of asm

    private final List<CodeElement> codeElementList;

    public InstructionsWrapper(List<CodeElement> codeElementList) {
        this.codeElementList = codeElementList;
    }

    public InstructionsWrapper(MethodModelCopy methodModel) {
        if (!methodModel.hasCode()) {
            this.codeElementList = new ArrayList<>();
        } else {
            this.codeElementList = methodModel.instructions();
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
        return new InstructionWrapper(codeElementList.getLast(), codeElementList, codeElementList.size() - 1);
    }

    public InstructionWrapper get(int index) {
        return new InstructionWrapper(codeElementList.get(index), codeElementList, index);
    }

    public InstructionWrapper firstRealInstruction() {
        for (int i = 0; i < codeElementList.size(); i++) {
            if (codeElementList.get(i) instanceof Instruction) {
                return new InstructionWrapper(codeElementList.get(i), codeElementList, i);
            }
        }
        return null;
    }

    public static class InstructionWrapper {
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
            return new InstructionWrapper(codeElementList.get(index + 1), codeElementList, index + 1);
        }

        public InstructionWrapper getPrevious() {
            if (index -1 < 0) {
                return null;  // TODO should throw an exception instead????
            }
            return new InstructionWrapper(codeElementList.get(index - 1), codeElementList, index - 1);
        }

        public InstructionWrapper nextRealInstruction() {
            InstructionWrapper instruction = this;
            while (instruction != null) {
                instruction = instruction.getNext();
                if (instruction != null && instruction.getCodeElement() instanceof Instruction) {
                    return instruction;
                }
            }
            return null;
        }

        public InstructionWrapper previousRealInstruction() {
            InstructionWrapper instruction = this;
            instruction = instruction.getPrevious();
            if (instruction != null && instruction.getCodeElement() instanceof Instruction) {
                return instruction;
            }
            return null;
        }

        public boolean isRealInstruction() {
            return this.codeElement instanceof Instruction;
        }

        public int getLineNumber() {
            InstructionWrapper currentIns = this;
            while (currentIns != null) {
                currentIns = currentIns.getPrevious();
                if (currentIns.codeElement instanceof LineNumber) {
                   return  ((LineNumber) currentIns.codeElement).line();
                }
            }
            return -1;
        }


    }
}
