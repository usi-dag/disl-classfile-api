package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.usi.dag.disl.exception.MarkerException;
import ch.usi.dag.disl.util.AsmOpcodes;

/**
 * <p>
 * Marks one java bytecode instruction.
 * 
 * <p>
 * Sets the start before a bytecode instruction and the end after a bytecode
 * instruction even if it is jump instruction.
 * 
 * <p>
 * <b>note:</b> Especially for jump instruction, this marker does NOT guarantee
 * that if the before is invoked, consequently, the after will be invoked.
 */
public class StrictBytecodeMarker extends AbstractInsnMarker {

    protected Set<Integer> searchedInstrNums = new HashSet<Integer>();

    public StrictBytecodeMarker(Parameter param) throws MarkerException {

        // translate all instructions to opcodes
        for (String instr : param.getMultipleValues(",")) {

            try {

                AsmOpcodes opcode = AsmOpcodes.valueOf(instr.trim()
                        .toUpperCase());
                searchedInstrNums.add(opcode.getNumber());
            } catch (IllegalArgumentException e) {

                throw new MarkerException("Instruction \"" + instr
                        + "\" cannot be found. See "
                        + AsmOpcodes.class.getName()
                        + " enum for list of possible instructions");
            }
        }

        if (searchedInstrNums.isEmpty()) {
            throw new MarkerException("Bytecode marker cannot operate without"
                    + " selected instructions. Pass instruction list using"
                    + " \"param\" annotation attribute.");
        }
    }


    @Override
    public List<CodeElement> markInstruction(MethodModel methodModel) {
        List<CodeElement> selected = new LinkedList<>();

        if (methodModel.code().isEmpty()) {
            return selected;
        }

        for (CodeElement codeElement: methodModel.code().get().elementList()) {
            if (codeElement instanceof Instruction && searchedInstrNums.contains(((Instruction) codeElement).opcode().bytecode())) {
                selected.add(codeElement);
            }
        }

        return selected;
    }
}
