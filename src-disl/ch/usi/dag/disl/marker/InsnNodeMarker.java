package ch.usi.dag.disl.marker;

import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import ch.usi.dag.disl.exception.MarkerException;

/**
 * Marks bytecode instructions depending on the ClassFile class type.
 * <p>
 * <b>Note:</b> This class is work in progress.
 */
// TODO this is not tested
public class InsnNodeMarker extends AbstractInsnMarker {

    protected Set<Class<? extends CodeElement>> classes;

    public InsnNodeMarker(Parameter param)
            throws MarkerException {

        classes = new HashSet<Class<? extends CodeElement>>();


        // translate all instructions to opcodes
        for (String className : param.getMultipleValues(",")) {

            try {

                Class<?> clazz = Class.forName(className);
                classes.add(clazz.asSubclass(CodeElement.class));
            } catch (ClassNotFoundException e) {

                throw new MarkerException("Instruction Node Class \""
                        + className + "\" cannot be found.");
            } catch (ClassCastException e) {

                throw new MarkerException("Class \"" + className
                        + "\" is not an instruction node class.");
            }
        }

        if (classes.isEmpty()) {
            throw new MarkerException(
                    "Instruction node class should be passed as a parameter.");
        }
    }

    @Override
    public List<CodeElement> markInstruction(MethodModel methodNode) {

        List<CodeElement> seleted = new LinkedList<CodeElement>();

        for (CodeElement instr : methodNode.code().orElseThrow().elementList()) {

            for (Class<? extends CodeElement> clazz : classes) {

                if (clazz.isInstance(instr)) {
                    seleted.add(instr);
                }
            }
        }

        return seleted;
    }

}
