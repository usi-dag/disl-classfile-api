package ch.usi.dag.disl.localvar;

import java.lang.classfile.CodeElement;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;


public final class ThreadLocalVar extends AbstractLocalVar {

    private final boolean __inheritable;
    private List<CodeElement> __initializerInstructions = new ArrayList<>(); // TODO pass here the instructions that initialize the variable

    public ThreadLocalVar(
            final String className, final String fieldName, final ClassDesc typeDesc, final boolean inheritable
            ) {
        super(className, fieldName, typeDesc);
        __inheritable = inheritable;
    }


    /**
     * Determines whether the initial value of this variable should
     * be inherited from the current {@link Thread}.
     *
     * @return {@code true} if the variable value is inheritable.
     */
    public boolean isInheritable () {
        return __inheritable;
    }


    /**
     * set the instructions that will be used to initialize the variable
     * @param __initializerInstructions list of instruction that initialize this variable
     */
    public void setInitializerInstructions(List<CodeElement> __initializerInstructions) {
        this.__initializerInstructions = __initializerInstructions;
    }

    /**
     * get the instructions to initialize the variable (not included the assignment PUTSTATIC)
     * @return the instructions
     */
    public List<CodeElement> getInitializerInstructions() {
        return this.__initializerInstructions;
    }
}
