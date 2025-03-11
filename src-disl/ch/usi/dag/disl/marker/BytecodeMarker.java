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
 * Marks one bytecode instruction.
 * <p>
 * Sets the start before a bytecode instruction and the end after a bytecode
 * instruction. If the bytecode instruction is (conditional) jump the end is
 * also inserted before the instruction (preserves before-after semantics).
 */
public class BytecodeMarker extends AbstractDWRMarker {

    protected Set <Integer> searchedInstrNums = new HashSet <Integer> ();


    public BytecodeMarker (final Parameter param) throws MarkerException {

        // translate all instructions to opcodes
        for (final String instr : param.getMultipleValues (",")) {
            try {
                final AsmOpcodes opcode = AsmOpcodes.valueOf (
                    instr.trim ().toUpperCase ()
                );

                searchedInstrNums.add (opcode.getNumber ());
            } catch (final IllegalArgumentException e) {
                throw new MarkerException (
                    "Instruction \""+ instr +"\" cannot be found. "+
                    "See the "+ AsmOpcodes.class.getName () +" enum for "+
                    "the list of valid instructions."
                );
            }
        }

        if (searchedInstrNums.isEmpty ()) {
            throw new MarkerException ("Bytecode marker cannot operate without" +
                " selected instructions. Pass instruction list using" +
                " \"param\" annotation attribute.");
        }
    }

    @Override
    public List<MarkedRegion> markWithDefaultWeavingReg(final MethodModel methodModel) {
        final List<MarkedRegion> regions = new LinkedList<>();
        if (methodModel.code().isEmpty()) {
            return regions;
        }
        for (final CodeElement element: methodModel.code().get()) {
            if (element instanceof Instruction && searchedInstrNums.contains(((Instruction) element).opcode().bytecode())) {
                regions.add(new MarkedRegion(element, element));
            }
        }
        return regions;
    }
}
