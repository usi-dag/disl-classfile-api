package ch.usi.dag.disl.staticcontext;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;

/**
 * Provides static context information about instrumented bytecode.
 * <p>
 * <b>Note:</b> This class is being deprecated. Please use the
 * {@link InstructionStaticContext} class instead.
 */
@Deprecated
public class BytecodeStaticContext extends AbstractStaticContext {

	/**
	 * Returns (ASM) integer number of the instrumented bytecode.
	 */
	public int getBytecodeNumber() {
		CodeElement element = staticContextData.getRegionStart();
		if (element instanceof Instruction instruction) {
			return instruction.opcode().bytecode();
		} else {
			// this is to replicate the ASM behaviour when an element is pseudo instruction
			return -1;
		}
	}
}
