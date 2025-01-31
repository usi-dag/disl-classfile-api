package ch.usi.dag.disl.test.suite.pe3.instr;

import org.objectweb.asm.Opcodes;

import ch.usi.dag.disl.annotation.Before;
import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.staticcontext.ClassStaticContext;
import ch.usi.dag.disl.staticcontext.InstructionStaticContext;
import ch.usi.dag.disl.staticcontext.MethodStaticContext;


public class DiSLClass {

    @Before(marker = BytecodeMarker.class, args = "putfield, getfield", scope = "TargetClass.public_method", order = 0)
    public static void beforeFieldAccess(
        final InstructionStaticContext insn,
        final ClassStaticContext csc, final MethodStaticContext msc
    ) {
        //
        // Keep the code as is. The partial evaluator should be able to construct
        // a string literal for each instatiation of the snippet
        //
        String operationID;

        if (insn.getOpcode() == Opcodes.PUTFIELD) {
            operationID = "disl: write:";
        } else {
            operationID = "disl: read:";
        }

        operationID += csc.getInternalName() + ":" + msc.thisMethodName() + ":"
                + msc.thisMethodDescriptor();
        System.out.println(operationID);
    }

}
