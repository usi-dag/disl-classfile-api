package ch.usi.dag.disl.test.suite.gettarget.instr;


import ch.usi.dag.disl.staticcontext.AbstractStaticContext;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;


public class GetTargetAnalysis extends AbstractStaticContext {

    public boolean isCalleeStatic() {
        CodeElement instr = staticContextData.getRegionStart();
        if (instr instanceof Instruction instruction) {
            return instruction.opcode() == Opcode.INVOKESTATIC;
        } else {
            return false;
        }
    }

    public int calleeParCount() {
        CodeElement instr = staticContextData.getRegionStart();

        if (instr instanceof InvokeInstruction instruction) {
            return instruction.typeSymbol().parameterCount();
        } else {
            return 0;
        }
    }

}
