package ch.usi.dag.disl.weaver.peClassFile;

import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import ch.usi.dag.disl.util.ClassFileAnalyzer.Analyzer;
import ch.usi.dag.disl.util.ClassFileAnalyzer.AnalyzerException;
import ch.usi.dag.disl.util.ClassFileAnalyzer.Frame;
import ch.usi.dag.disl.util.ClassFileAnalyzer.SourceValue;
import ch.usi.dag.disl.util.ClassFileFrameHelper;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.cfgCF.BasicBlockCF;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;

import static java.lang.constant.ConstantDescs.CD_void;

public class PartialEvaluator {

    private final List<CodeElement> instructions;
    private final List<ExceptionCatch> exceptionCatches;
    private final MethodTypeDesc methodTypeDesc;
    private final AccessFlags flags;
    private final Map<Label, CodeElement> labelTargetMap;


    public PartialEvaluator(List<CodeElement> instructions, List<ExceptionCatch> exceptionCatches,
                            MethodTypeDesc methodTypeDesc, AccessFlags flags) {
        this.instructions = instructions;
        this.exceptionCatches = exceptionCatches;
        this.methodTypeDesc = methodTypeDesc.changeReturnType(CD_void);
        this.flags = flags;
        this.labelTargetMap = ClassFileHelper.getLabelTargetMap(instructions);
    }

    public List<CodeElement> getInstructions() {
        return this.instructions;
    }


    private boolean removeUnusedBB(ControlFlowGraph cfg) {
        boolean isOptimized = false;
        boolean changed = true;

        List<BasicBlockCF> connected = new LinkedList<>(cfg.getNodes());

        connected.remove(cfg.getBasicBlock(instructions.getFirst()));

        for (ExceptionCatch tbc: exceptionCatches) {
            connected.remove(cfg.getBasicBlock(labelTargetMap.get(tbc.handler())));
        }

        while (changed) {
            changed = false;
            List<BasicBlockCF> removed = new LinkedList<>();

            for (BasicBlockCF bb: connected) {
                if (!bb.getPredecessor().isEmpty()) {
                    continue;
                }

                changed = true;
                CodeElement prev = null;
                CodeElement iter = bb.getEntry();

                while (prev != bb.getExit()) {
                    prev = iter;
                    iter = ClassFileHelper.nextInstruction(instructions, iter);

                    if (prev instanceof Instruction instruction) {
                        if (instruction.opcode() != Opcode.RETURN) {
                            isOptimized = true;
                            instructions.remove(prev);
                        }
                    }
                }

                for (BasicBlockCF successor: bb.getSuccessors()) {
                    successor.getPredecessor().remove(bb);
                }

                removed.add(bb);
            }
            connected.removeAll(removed);
        }

        return isOptimized;
    }


    private boolean conditionalReduction(
            Map<CodeElement, Frame<ConstValue>> frames
    ) throws AnalyzerException {
        boolean isOptimized = false;
        ControlFlowGraph cfg = ControlFlowGraph.build(instructions, exceptionCatches);

        for (BasicBlockCF bb: cfg.getNodes()) {
            Instruction instruction = ClassFileHelper.firstPreviousRealInstruction(instructions, bb.getExit());
            Opcode opcode = instruction.opcode();
            Frame<ConstValue> frame = frames.get(instruction);

            switch (instruction) {
                case BranchInstruction branchInstruction -> {
                    ConstValue result = null;
                    boolean popTwice = false;
                    switch (opcode) {
                        case Opcode.GOTO:
                        case Opcode.GOTO_W:
                            continue;

                        case Opcode.IF_ICMPEQ:
                        case Opcode.IF_ICMPNE:
                        case Opcode.IF_ICMPLT:
                        case Opcode.IF_ICMPGE:
                        case Opcode.IF_ICMPGT:
                        case Opcode.IF_ICMPLE:
                        case Opcode.IF_ACMPEQ:
                        case Opcode.IF_ACMPNE: {
                            ConstValue value1 = ClassFileFrameHelper.getStackByIndex(frame, 1);
                            ConstValue value2 = ClassFileFrameHelper.getStackByIndex(frame, 0);
                            result = ConstInterpreter.getInstance().binaryOperation(
                                    instruction, value1, value2);
                            popTwice = true;
                            break;
                        }
                        default: {
                            ConstValue value = ClassFileFrameHelper.getStackByIndex(frame, 0);
                            result = ConstInterpreter.getInstance().unaryOperation(instruction, value);
                            break;
                        }
                    }
                    if (result.cst == null) {
                        continue;
                    }
                    if ((Boolean) result.cst) {
                        BasicBlockCF successor = cfg.getBasicBlock(
                                ClassFileHelper.nextInstruction(instructions, instruction));
                        bb.getSuccessors().remove(successor);
                        if (popTwice) {
                            ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);
                        }
                        ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);

                        ClassFileHelper.insertBefore(instruction,
                                BranchInstruction.of(Opcode.GOTO, branchInstruction.target()), instructions);
                        bb.setExit(ClassFileHelper.previousInstruction(instructions, instruction));
                        instructions.remove(instruction);
                    } else {
                        BasicBlockCF successor = cfg.getBasicBlock(
                                labelTargetMap.get(branchInstruction.target()));
                        bb.getSuccessors().remove(successor);
                        successor.getPredecessor().remove(bb);

                        if (popTwice) {
                            ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);
                        }
                        ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);
                        bb.setExit(
                                ClassFileHelper.previousInstruction(instructions, instruction)
                        );
                        instructions.remove(instruction);
                    }
                    isOptimized = true;
                    break;
                }
                case LookupSwitchInstruction lookupSwitchInstruction -> {

                    ConstValue value = ClassFileFrameHelper.getStackByIndex(frame, 0);
                    if (value.cst == null) {
                        continue;
                    }

                    List<SwitchCase> switchCases = lookupSwitchInstruction.cases();
                    Map<Integer, Label> mapIndexLabel = switchCases.stream().collect(Collectors.toMap(
                            SwitchCase::caseValue, SwitchCase::target));
                    Label label = null;
                    // value.cst should be an integer
                    // TODO is this actually the same as the original version?
                    if (mapIndexLabel.containsKey(value.cst)) {
                        BasicBlockCF successor = cfg.getBasicBlock(
                                labelTargetMap.get(lookupSwitchInstruction.defaultTarget()));
                        bb.getSuccessors().remove(successor);
                        successor.getPredecessor().remove(bb);
                    } else {
                        label = lookupSwitchInstruction.defaultTarget();
                    }

                    for (SwitchCase currentCase: switchCases) {
                        if ((Integer) value.cst == currentCase.caseValue()) {
                            label = currentCase.target();
                            continue;
                        }
                        BasicBlockCF successor = cfg.getBasicBlock(
                                labelTargetMap.get(currentCase.target()));
                        bb.getSuccessors().remove(successor);
                        successor.getPredecessor().remove(bb);
                    }

                    ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);
                    ClassFileHelper.insertBefore(instruction, BranchInstruction.of(Opcode.GOTO, label), instructions);
                    bb.setExit(
                            ClassFileHelper.previousInstruction(instructions, instruction)
                    );
                    instructions.remove(instruction);
                    isOptimized = true;

                }
                case TableSwitchInstruction tableSwitchInstruction -> {
                    ConstValue value = ClassFileFrameHelper.getStackByIndex(frame, 0);

                    if (value.cst == null) {
                        continue;
                    }

                    int index = (Integer) value.cst;
                    Label label = null;
                    // TODO shouldn't be a || instead of a && ???? even the original is like this
                    if (index < tableSwitchInstruction.lowValue() && index > tableSwitchInstruction.highValue()) {
                        BasicBlockCF successor = cfg.getBasicBlock(
                                labelTargetMap.get(tableSwitchInstruction.defaultTarget()));
                        bb.getSuccessors().remove(successor);
                        successor.getPredecessor().remove(bb);
                    } else {
                        label = tableSwitchInstruction.defaultTarget();
                    }

                    for (int i = tableSwitchInstruction.lowValue(); i <= tableSwitchInstruction.highValue(); i++) {
                        if (i == index) {
                            label = tableSwitchInstruction.cases().get(i - tableSwitchInstruction.lowValue()).target();
                            continue;
                        }

                        BasicBlockCF successor = cfg.getBasicBlock(
                                labelTargetMap.get(
                                        tableSwitchInstruction.cases().get(i - tableSwitchInstruction.lowValue()).target()
                                )
                        );
                        bb.getSuccessors().remove(successor);
                        successor.getPredecessor().remove(bb);
                    }

                    ClassFileHelper.insertBefore(instruction, StackInstruction.of(Opcode.POP), instructions);
                    ClassFileHelper.insertBefore(instruction, BranchInstruction.of(Opcode.GOTO, label), instructions);
                    bb.setExit(ClassFileHelper.previousInstruction(instructions, instruction));
                    instructions.remove(instruction);
                    isOptimized = true;
                    break;
                }
                default -> {}
            }
        }
        return removeUnusedBB(cfg) | isOptimized;
    }


    private boolean insertLoadConstant(List<CodeElement> ins, CodeElement location, Object cst) {
        if (cst == null) {
            return false;
        }
        if (cst == ConstValue.NULL) {
            ClassFileHelper.insertBefore(location, ConstantInstruction.ofIntrinsic(Opcode.ACONST_NULL), ins);
            return true;
        }

        ClassFileHelper.insertBefore(location, ClassFileHelper.loadConst(cst), ins);
        return true;
    }


    private boolean replaceLoadWithLDC(
            Map<CodeElement, Frame<ConstValue>> frames
    ) throws AnalyzerException {
        boolean isOptimized = false;

        for (CodeElement instr: instructions.toArray(new CodeElement[0])) {
            Frame<ConstValue> frame = frames.get(instr);

            if (frame == null) {
                continue;
            }

            if (!(instr instanceof Instruction instruction)) {
                continue;
            }

            if (ConstInterpreter.mightBeUnaryConstOperation(instruction)) {
                ConstValue value = ClassFileFrameHelper.getStackByIndex(frame, 0);
                Object cst = ConstInterpreter.getInstance().unaryOperation(
                        instruction, value).cst;

                if (insertLoadConstant(instructions, instr, cst)) {
                    CodeElement previous = ClassFileHelper.previousInstruction(instructions, instr);
                    ClassFileHelper.insertBefore(previous, StackInstruction.of(
                            value.size == 1? Opcode.POP: Opcode.POP2), instructions);
                    instructions.remove(instr);
                    isOptimized = true;
                }
                continue;

            } else if (ConstInterpreter.mightBeBinaryConstOperation(instruction)) {
                ConstValue value1 = ClassFileFrameHelper.getStackByIndex(frame, 1);
                ConstValue value2 = ClassFileFrameHelper.getStackByIndex(frame, 0);
                Object cts = ConstInterpreter.getInstance().binaryOperation(
                        instruction, value1, value2).cst;

                if (insertLoadConstant(instructions, instruction, cts)) {
                    CodeElement previous = ClassFileHelper.previousInstruction(instructions, instr);
                    ClassFileHelper.insertBefore(previous, StackInstruction.of(
                            value2.size == 1? Opcode.POP: Opcode.POP2), instructions);
                    ClassFileHelper.insertBefore(previous, StackInstruction.of(
                            value1.size == 1? Opcode.POP: Opcode.POP2), instructions);
                    instructions.remove(instr);
                    isOptimized = true;
                }
                continue;
            }

            if (instruction instanceof LoadInstruction loadInstruction) {
                if (insertLoadConstant(instructions, instr,
                        frame.getLocal(loadInstruction.slot()).cst)
                ) {
                    instructions.remove(instr);
                    isOptimized = true;
                }
            }
        }
        return isOptimized;
    }

    private boolean loadAfterStore(BasicBlockCF bb, CodeElement instr, int var) {
        CodeElement previous = ClassFileHelper.previousInstruction(instructions, instr);

        while (previous != bb.getExit()) {
            if (instr instanceof LoadInstruction loadInstruction) {
                if (loadInstruction.slot() == var) {
                    return true;
                }
            } else {
                previous = instr;
                instr = ClassFileHelper.nextInstruction(instructions, instr);
            }
        }
        return false;
    }


    private boolean deadStore(ControlFlowGraph cfg, StoreInstruction store) {
        BasicBlockCF bb = cfg.getBasicBlock(store);
        if (bb == null) {
            return false;
        }

        if (loadAfterStore(bb, store, store.slot())) {
            return false;
        }

        HashSet<BasicBlockCF> visited = new HashSet<>();
        Queue<BasicBlockCF> unprocessed = new LinkedList<>(
                bb.getSuccessors());

        while (!unprocessed.isEmpty()) {
            BasicBlockCF next = unprocessed.poll();

            if (visited.contains(next)) {
                continue;
            }
            if (loadAfterStore(next, next.getEntry(), store.slot())) {
                return false;
            }
            visited.add(next);
        }
        return true;
    }


    private boolean removeDeadStore() {

        ControlFlowGraph cfg = ControlFlowGraph.build(instructions, exceptionCatches);
        boolean isOptimized = false;
        for (CodeElement instr: instructions.toArray(new CodeElement[0])) {
            if (instr instanceof StoreInstruction storeInstruction) {
                switch (storeInstruction.opcode()) {
                    case ISTORE,
                         ISTORE_0,
                         ISTORE_1,
                         ISTORE_2,
                         ISTORE_3,
                         ISTORE_W,
                         ASTORE,
                         ASTORE_0,
                         ASTORE_1,
                         ASTORE_2,
                         ASTORE_3,
                         ASTORE_W,
                         FSTORE,
                         FSTORE_0,
                         FSTORE_1,
                         FSTORE_2,
                         FSTORE_3,
                         FSTORE_W -> {
                        if (deadStore(cfg, storeInstruction)) {
                            ClassFileHelper.insertBefore(instr,
                                    StackInstruction.of(Opcode.POP), instructions);
                            instructions.remove(instr);
                            isOptimized = true;
                        }
                    }
                    case DSTORE,
                         DSTORE_0,
                         DSTORE_1,
                         DSTORE_2,
                         DSTORE_3,
                         DSTORE_W,
                         LSTORE,
                         LSTORE_0,
                         LSTORE_1,
                         LSTORE_2,
                         LSTORE_3,
                         LSTORE_W -> {
                        if (deadStore(cfg, storeInstruction)) {
                            ClassFileHelper.insertBefore(instr,
                                    StackInstruction.of(Opcode.POP2), instructions);
                            instructions.remove(instr);
                            isOptimized = true;
                        }
                    }
                }
            }
        }
        return isOptimized;
    }


    private boolean unremovablePop(Set<CodeElement> sources) {
        for (CodeElement source: sources) {
            switch (source) {
                case LoadInstruction _,
                     ConstantInstruction _,
                     NewObjectInstruction _ -> {
                    // do nothing
                }
                case InvokeInstruction invokeInstruction -> {
                   if (invokeInstruction.opcode() == Opcode.INVOKEINTERFACE) {
                       return true;
                   }
                   if (!InvocationInterpreter.getInstance().isRegistered(invokeInstruction)) {
                       return true;
                   }
                }
                default -> {
                    return true;
                }
            }
        }
        return false;
    }


    private void tryRemoveInvocation(List<CodeElement> ins, InvokeInstruction invokeInstruction) {
        if (InvocationInterpreter.getInstance().isRegistered(invokeInstruction)) {
            MethodTypeDesc desc = invokeInstruction.typeSymbol();

            if (invokeInstruction.opcode() == Opcode.INVOKEVIRTUAL) {
                ClassFileHelper.insert(invokeInstruction, StackInstruction.of(Opcode.POP), instructions);
            }
            for (ClassDesc arg: desc.parameterArray()) {
                TypeKind type = TypeKind.from(arg);
                ClassFileHelper.insert(invokeInstruction, StackInstruction.of(
                        type.slotSize() == 2? Opcode.POP2: Opcode.POP
                ), instructions);
            }
        }
    }


    private void tryRemoveAllocation(List<CodeElement> instructionsList, CodeElement next,
                                     Map<CodeElement, Frame<SourceValue>> frames) {
        if (!(next instanceof Instruction) || ((Instruction) next).opcode() == Opcode.DUP) {
            return;
        }

        for (CodeElement instr: instructionsList.toArray(new CodeElement[0])) {
            if (instr instanceof InvokeInstruction invokeInstruction) {
                if (invokeInstruction.opcode() == Opcode.INVOKESPECIAL) {
                    MethodTypeDesc desc = invokeInstruction.typeSymbol();
                    ClassDesc[] args = desc.parameterArray();
                    Frame<SourceValue> frame = frames.get(instr);
                    Set<CodeElement> sources = ClassFileFrameHelper.getStackByIndex(
                            frame, args.length
                    ).instructions;
                    if (sources.contains(next)) {
                        for (ClassDesc arg: args) {
                            TypeKind type = TypeKind.from(arg);
                            ClassFileHelper.insert(
                                    instr,
                                    type.slotSize() == 2 ? StackInstruction.of(Opcode.POP2):
                                            StackInstruction.of(Opcode.POP),
                                    instructionsList);
                        }

                        instructionsList.remove(instr);
                    }
                }
            }
        }

        instructionsList.remove(next);
    }


    private boolean removePop() {
        Map<CodeElement, Frame<SourceValue>> frames =
                ClassFileFrameHelper.createMapping(
                        ClassFileFrameHelper.getSourceAnalyzer(),
                        ClassDesc.ofDescriptor(PartialEvaluator.class.descriptorString()),
                        instructions,
                        exceptionCatches,
                        methodTypeDesc,
                        flags);

        boolean isOptimized = false;

        for (CodeElement instr: instructions.toArray(new CodeElement[0])) {
            if (instr instanceof StackInstruction stackInstruction) {
                Opcode opcode = stackInstruction.opcode();
                if (opcode != Opcode.POP && opcode != Opcode.POP2) {
                    continue;
                }

                Frame<SourceValue> frame = frames.get(instr);

                if (frame == null) {
                    continue;
                }

                Set<CodeElement> sources = ClassFileFrameHelper.getStackByIndex(frame, 0).instructions;

                if (unremovablePop(sources)) {
                    continue;
                }

                for (CodeElement source: sources) {
                    switch (source) {
                        case InvokeInstruction invokeInstruction -> {
                            Opcode invokeOpcode = invokeInstruction.opcode();
                            if (invokeOpcode == Opcode.INVOKESPECIAL ||
                                invokeOpcode == Opcode.INVOKEVIRTUAL ||
                                invokeOpcode == Opcode.INVOKESTATIC) {
                                tryRemoveInvocation(instructions, invokeInstruction);
                            }
                        }
                        case NewObjectInstruction newObjectInstruction -> {
                            tryRemoveAllocation(instructions,
                                    ClassFileHelper.nextInstruction(instructions, source),
                                    frames);
                        }
                        default -> {}
                    }
                    instructions.remove(source);
                }
                instructions.remove(instr);
                isOptimized = true;
            }
        }
        return isOptimized;
    }


    private boolean removeUnusedJump() {
        boolean isOptimized = false;

        for (CodeElement instr: instructions.toArray(new CodeElement[0])) {
            switch (instr) {
                case BranchInstruction branchInstruction -> {
                    Label target = branchInstruction.target();
                    CodeElement labelTarget = labelTargetMap.get(target);
                    if (ClassFileHelper.firstPreviousRealInstruction(instructions, labelTarget) != instr) {
                        continue;
                    }

                    if (branchInstruction.opcode() != Opcode.GOTO || branchInstruction.opcode() != Opcode.GOTO_W) {
                        ClassFileHelper.insertBefore(instr, StackInstruction.of(Opcode.POP), instructions);
                    }

                    instructions.remove(instr);
                    isOptimized = true;
                }
                case LookupSwitchInstruction lookupSwitchInstruction -> {
                    boolean flag = false;
                    for (SwitchCase switchCase: lookupSwitchInstruction.cases()) {
                        CodeElement labelTarget = labelTargetMap.get(switchCase.target());
                        if (ClassFileHelper.firstPreviousRealInstruction(instructions, labelTarget) != instr) {
                            flag = true;
                        }
                    }
                    CodeElement defaultTarget = labelTargetMap.get(lookupSwitchInstruction.defaultTarget());
                    if (flag ||
                            ClassFileHelper.firstPreviousRealInstruction(instructions, defaultTarget) != instr) {
                        continue;
                    }

                    ClassFileHelper.insertBefore(instr, StackInstruction.of(Opcode.POP), instructions);
                    instructions.remove(instr);
                    isOptimized = true;
                }
                case TableSwitchInstruction tableSwitchInstruction -> {
                    boolean flag = false;
                    for (SwitchCase switchCase: tableSwitchInstruction.cases()) {
                        CodeElement labelTarget = labelTargetMap.get(switchCase.target());
                        if (ClassFileHelper.firstPreviousRealInstruction(instructions, labelTarget) != instr) {
                            flag = true;
                        }

                        CodeElement defaultTarget = labelTargetMap.get(tableSwitchInstruction.defaultTarget());
                        if (flag ||
                                ClassFileHelper.firstPreviousRealInstruction(instructions, defaultTarget) != instr) {
                            continue;
                        }

                        ClassFileHelper.insertBefore(instr, StackInstruction.of(Opcode.POP), instructions);
                        instructions.remove(instr);
                        isOptimized = true;
                    }
                }
                default -> {}
            }
        }

        return isOptimized;
    }


    private boolean removeUnusedHandler() {
        ControlFlowGraph cfg = ControlFlowGraph.build(instructions, exceptionCatches);
        boolean isOptimized = false;

        // TODO here I made iterate over a copy since we are modifying the list
        for (final ExceptionCatch tcb: exceptionCatches.toArray(new ExceptionCatch[0])) {
            CodeElement first = labelTargetMap.get(tcb.tryStart());
            CodeElement last = labelTargetMap.get(tcb.tryEnd());
            if (first == last) {
                exceptionCatches.remove(tcb);
                isOptimized |= removeUnusedBB(cfg);
            }
        }
        return isOptimized;
    }


    public boolean evaluate() throws AnalyzerException {
        instructions.add(ReturnInstruction.of(Opcode.RETURN));
        Analyzer<ConstValue> constAnalyzer = new Analyzer<ConstValue>(ConstInterpreter.getInstance());

        Map<CodeElement, Frame<ConstValue>> frames = ClassFileFrameHelper.createMapping(
                constAnalyzer, ClassDesc.ofDescriptor(PartialEvaluator.class.descriptorString()), instructions, exceptionCatches, methodTypeDesc, flags
        );

        boolean isOptimized = conditionalReduction(frames);
        isOptimized |= replaceLoadWithLDC(frames);

        boolean removed;

        do {
            removed = removeDeadStore();
            removed |= removePop();
        } while (removed);

        isOptimized |= removeUnusedJump();
        isOptimized |= removeUnusedHandler();

        instructions.remove(instructions.getLast());

        return isOptimized;
    }
}
