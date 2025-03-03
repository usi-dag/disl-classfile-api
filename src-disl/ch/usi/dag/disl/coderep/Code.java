package ch.usi.dag.disl.coderep;

import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ExceptionCatch;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ch.usi.dag.disl.localvar.LocalVars;
import ch.usi.dag.disl.localvar.SyntheticLocalVar;
import ch.usi.dag.disl.localvar.ThreadLocalVar;
import ch.usi.dag.disl.util.ClassFileHelper;


/**
 * Represents an analyzed and partially expanded code template. Instances of
 * {@link Code} are obtained from {@link UnprocessedCode} instances as a result
 * of calling the {@link UnprocessedCode#process(LocalVars) process()} method on
 * them.
 */
public class Code {

    /** A method representing the code template. */
    private MethodModel __method;

    /** Synthetic-local variables referenced by the code template. */
    private final Set <SyntheticLocalVar> __syntheticLocals;

    /** Thread-local variables referenced by the code template. */
    private final Set <ThreadLocalVar> __threadLocals;

    /** Static context methods invoked by the code template. */
    private final Set <StaticContextMethod> __staticContextMethods;

    /**
     * Determines whether the code contains an exception handler that handles an
     * exception and does not propagate it. This may cause stack inconsistency
     * which needs to be handled.
     */
    private final boolean _containsHandledException;

    //

    public Code (
        final MethodModel method,
        final Set <SyntheticLocalVar> syntheticLocals,
        final Set <ThreadLocalVar> threadLocals,
        final Set <StaticContextMethod> staticContextMethods,
        final boolean containsHandledException
    ) {
        __method = method; // caller responsible for giving us a clone

        __syntheticLocals = Collections.unmodifiableSet (syntheticLocals);
        __threadLocals = Collections.unmodifiableSet (threadLocals);
        __staticContextMethods = Collections.unmodifiableSet (staticContextMethods);

        _containsHandledException = containsHandledException;
    }


    // TODO need to find other way around instead of cloning
    private Code (final Code that) {
        __method = that.__method;

        // The following immutables can be shared.
        __syntheticLocals = that.__syntheticLocals;
        __threadLocals = that.__threadLocals;
        __staticContextMethods = that.__staticContextMethods;

        _containsHandledException = that._containsHandledException;
    }


    /**
     * @return An ASM instruction list.
     */
    public List<CodeElement> getInstructions () {
        if (__method.code().isEmpty()) {
            return new ArrayList<>();
        }
        return __method.code().get().elementList();
    }

    public MethodModel getMethod() {
        return this.__method;
    }


    /**
     * @return A list of try-catch blocks (as represented in ASM).
     */
    public List <ExceptionCatch> getTryCatchBlocks () {
        if (__method.code().isEmpty()) {
            return new ArrayList<>();
        }
        return __method.code().get().exceptionHandlers();
    }


    /**
     * @return An unmodifiable set of all synthetic local variables referenced
     *          in the code.
     */
    public Set <SyntheticLocalVar> getReferencedSLVs () {
        return __syntheticLocals;
    }


    /**
     * @return An unmodifiable set all thread local variables referenced in the
     *         code.
     */
    public Set <ThreadLocalVar> getReferencedTLVs () {
        return __threadLocals;
    }


    /**
     * @return An unmodifiable set of static context methods referenced in the
     *         code.
     */
    public Set <StaticContextMethod> getReferencedSCMs () {
        return __staticContextMethods;
    }


    /**
     * @return {@code true} if the code contains a catch block that handles the
     *         caught exception.
     */
    public boolean containsHandledException () {
        return _containsHandledException;
    }


    /**
     * @return the number of local slots occupied by parameters of the method
     *         representing this code.
     */
    public int getParameterSlotCount () {
        return ClassFileHelper.getParameterSlotCount(__method);
    }

    //

    /**
     * Creates a clone of this code. TODO CodeModel cannot be copied so this method is not working as intended
     */
    @Override
    public Code clone () {
        return new Code (this);
    }


    // this is needed in case the code of the method is transformed, the ClassFile api do not allow editing methods, but a new method of a new class must be created
    public void setNewMethod(MethodModel newMethod) {
        this.__method = newMethod;
    }

}
