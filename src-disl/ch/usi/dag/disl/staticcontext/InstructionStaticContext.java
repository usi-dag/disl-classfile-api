package ch.usi.dag.disl.staticcontext;

import ch.usi.dag.disl.util.ClassFileHelper;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.util.List;


/**
 * <b>NOTE: This class is work in progress</b> <br>
 * <br>
 * Provides static information about the first instruction of this instrumented
 * region.
 */
public class InstructionStaticContext extends AbstractStaticContext {

    /**
     * Returns the opcode of the first instruction of this instrumented region.
     */
    public int getOpcode () {
        final CodeElement insn = staticContextData.getRegionStart ();
        List<CodeElement> instructions = staticContextData.getMethodModel().instructions();
        return ClassFileHelper.firstNextRealInstruction(instructions, insn).opcode().bytecode();
    }


    /**
     * Returns a zero-based index of the first instruction of this instrumented
     * region in the method bytecode.
     */
    public int getIndex () {
        final CodeElement startInsn = staticContextData.getRegionStart ();
        final List<CodeElement> insns = staticContextData.getMethodModel ().instructions();

        //
        // There is a region, therefore there must be instructions.
        // No need to check for empty instruction list.
        //
        CodeElement insn = insns.getFirst ();

        int result = 0;
        while (insn != startInsn) {
            result += insn instanceof Instruction ? 1 : 0;
            insn = ClassFileHelper.nextInstruction(insns, insn);
        }

        return result;
    }

}
