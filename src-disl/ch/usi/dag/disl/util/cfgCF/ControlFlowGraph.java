package ch.usi.dag.disl.util.cfgCF;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.util.*;
import java.util.stream.Collectors;

import static ch.usi.dag.disl.util.ClassFileHelper.getLabelTargetMap;

public class ControlFlowGraph {

    private final List<CodeElement> instructions;  // list of all instruction (include labels and line numbers) to navigate,
    // since the classfile do not have a getNext and get previous method like asm
    private final List<ExceptionCatch> tryCatchBlocks;

    private final static int NOT_FOUND = -1;
    private final static int NEW = -2;
    
    private List<BasicBlockCF> nodes;   // basic blocks of a method
    
    private List<BasicBlockCF> connectedNodes;   // a basic block is marked as connected after visited
    
    private int connectedSize;   // size of connected basic blocks since last visit
    
    private Set<BasicBlockCF> methodExit;  // basic blocks that ends with a 'return' or 'athrow'


    public ControlFlowGraph(List<CodeElement> instructions, List<ExceptionCatch> tryCatchBlocks) {
        // in the ClassFile Api ExceptionCatch are included in the list of instruction as pseudoInstruction, we remove them to since we
        // have a separate list for them. in the instructions are included the real instructions as well as some pseudo instructions such as Labels and line Numbers
        this.instructions = instructions.stream().filter(i -> !(i instanceof ExceptionCatch)).toList();
        this.tryCatchBlocks = tryCatchBlocks;
        
        nodes = new LinkedList<>();
        connectedNodes = new LinkedList<>();
        connectedSize = 0;
        methodExit = new HashSet<>();

        // Generating basic blocks
        List<CodeElement> separators = BasicBlockCalculator.getAll(this.instructions, tryCatchBlocks, false);
        CodeElement last = instructions.getLast();
        separators.add(last);

        for (int i = 0; i < separators.size() -1; i++) {
            CodeElement start = separators.get(i);
            CodeElement end = separators.get(i + 1);

            if (i != separators.size() - 2) {
                end = ClassFileHelper.previousInstruction(instructions, end);
            }

            end = ClassFileHelper.firstPreviousRealInstruction(instructions, end);
            nodes.add(new BasicBlockCF(i, start, end, instructions));
        }

    }

    public ControlFlowGraph(MethodModelCopy methodModel) {
        if (!methodModel.hasCode()) {
            throw new RuntimeException("No code for method: " + methodModel.methodName().stringValue() + " while making a ControlFlowGraph");
        }
        this(methodModel.instructions(), methodModel.exceptionHandlers());
    }

    public List<BasicBlockCF> getNodes() {
        return nodes;
    }

    public List<CodeElement> getInstructions() {
        return this.instructions;
    }

    /**
     * @return the index of the basic block that contains the given instruction
     *         or -1 (NOT_FOUND) if the basic block could not be found.
     */
    public int getIndex(final CodeElement element) {
        final BasicBlockCF basicBlock = getBasicBlock(element);
        return (basicBlock != null) ? basicBlock.getIndex() : NOT_FOUND;
    }

    // Return a basic block that contains the input instruction. If not found, return null.
    public BasicBlockCF getBasicBlock(CodeElement element) {
        CodeElement instruction = ClassFileHelper.firstNextRealInstruction(instructions, element);
        while (instruction != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).getEntry().equals(instruction)) {
                    return nodes.get(i);
                }
            }
            instruction = ClassFileHelper.previousInstruction(instructions, instruction);
        }
        return null;
    }

    // Visit a successor. If the basic block, which starts with the input 'node', is not found, return NOT_FOUND;
    // If the basic block has been visited, then returns its index; Otherwise return NEW.
    private int tryVisit(BasicBlockCF current, CodeElement node) {
        BasicBlockCF basicBlock= getBasicBlock(node);
        if (basicBlock == null) {
            return NOT_FOUND;
        }

        if (connectedNodes.contains(basicBlock)) {
            int index = connectedNodes.indexOf(basicBlock);

            if (current != null) {
                if (index < connectedSize) {
                    current.getJoints().add(basicBlock);
                } else {
                    current.getSuccessors().add(basicBlock);
                    basicBlock.getPredecessor().add(current);
                }
            }
            return index;
        }

        if (current != null) {
            current.getSuccessors().add(basicBlock);
            basicBlock.getPredecessor().add(current);
        }

        connectedNodes.add(basicBlock);
        return NEW;
    }

    // Try to visit a successor. If it is visited last build, then regards it as an exit.
    private void tryVisit(BasicBlockCF current, CodeElement node, CodeElement exit, List<CodeElement> joints) {
        int ret = tryVisit(current, node);

        if (ret >= 0 && ret < connectedSize) {
            joints.add(exit);
        }
    }

    // Generate a control flow graph.
    // Returns a list of instruction that stands for the exit point of the current visit.
    // For the first time this method is called, it will generate the normal return of this method.
    // Otherwise, it will generate the join instruction between the current visit and a existing visit.
    public List<CodeElement> visit(CodeElement root) {
        List<CodeElement> joints = new LinkedList<>();

        if (tryVisit(null, root) == NOT_FOUND) {
            return joints;
        }

        Map<Label, CodeElement> labelTargetMap = getLabelTargetMap(instructions);

        for (int i = connectedSize; i < connectedNodes.size(); i++) {
            BasicBlockCF current = connectedNodes.get(i);
            CodeElement exit = current.getExit();

            switch (exit) {
                case BranchInstruction branch -> {
                    Label target = branch.target();
                    CodeElement labelTarget = labelTargetMap.get(target);
                    if (labelTarget != null) {
                        tryVisit(current, labelTarget, exit, joints);
                    }
                    if (branch.opcode() != Opcode.GOTO) {
                        tryVisit(current, ClassFileHelper.nextInstruction(instructions, exit), exit, joints);
                    }
                }
                case LookupSwitchInstruction lookup -> {
                    // TODO remove code duplication
                    Label defaultTarget = lookup.defaultTarget();
                    List<SwitchCase> cases = lookup.cases();
                    List<Label> allLabelsTarget = cases.stream().map(SwitchCase::target).toList();
                    List<CodeElement> actualTargets = allLabelsTarget.stream()
                            .filter(labelTargetMap::containsKey)
                            .map(labelTargetMap::get)
                            .toList();
                    for (CodeElement labelTarget: actualTargets) {
                        tryVisit(current, labelTarget, exit, joints);
                    }
                    if (labelTargetMap.containsKey(defaultTarget)) {
                        tryVisit(current, labelTargetMap.get(defaultTarget), exit, joints);
                    }
                }
                case TableSwitchInstruction tableSwitch -> {
                    Label defaultTarget = tableSwitch.defaultTarget();
                    List<SwitchCase> cases = tableSwitch.cases();
                    List<Label> allLabelsTarget = cases.stream().map(SwitchCase::target).toList();
                    List<CodeElement> actualTargets = allLabelsTarget.stream()
                            .filter(labelTargetMap::containsKey)
                            .map(labelTargetMap::get)
                            .toList();
                    for (CodeElement labelTarget: actualTargets) {
                        tryVisit(current, labelTarget, exit, joints);
                    }
                    if (labelTargetMap.containsKey(defaultTarget)) {
                        tryVisit(current, labelTargetMap.get(defaultTarget), exit, joints);
                    }
                }
                case ReturnInstruction _, ThrowInstruction _ -> methodExit.add(current);
                default -> {
                    CodeElement next = ClassFileHelper.nextInstruction(instructions, exit);
                    tryVisit(current, next, exit, joints);
                }
            }

        }
        connectedSize = connectedNodes.size();
        return joints;
    }

    public List<CodeElement> getEnds() {
        List<CodeElement> ends = new LinkedList<>();
        for (BasicBlockCF basicBlock: nodes) {
            if (basicBlock.getPredecessor().isEmpty()) {
                ends.add(basicBlock.getExit());
            }
        }
        return ends;
    }

    public static ControlFlowGraph build(List<CodeElement> instructions, List<ExceptionCatch> tryCatchBlocks) {
        ControlFlowGraph cfg= new ControlFlowGraph(instructions, tryCatchBlocks);
        cfg.visit(instructions.stream().filter(i -> !(i instanceof ExceptionCatch)).findFirst().get());

        Map<Label, LabelTarget> labelTargetMap = instructions.stream()
                .filter(i -> i instanceof LabelTarget)
                .map(i -> (LabelTarget)i).
                collect(Collectors.toMap(LabelTarget::label, i -> i));

        for (ExceptionCatch exceptionCatch: tryCatchBlocks) {
            if (labelTargetMap.containsKey(exceptionCatch.handler())) {
                cfg.visit(labelTargetMap.get(exceptionCatch.handler()));
            }

        }
        return cfg;
    }
}
