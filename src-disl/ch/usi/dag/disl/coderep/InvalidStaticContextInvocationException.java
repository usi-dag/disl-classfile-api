package ch.usi.dag.disl.coderep;


import java.lang.classfile.instruction.InvokeInstruction;


@SuppressWarnings ("serial")
final class InvalidStaticContextInvocationException extends RuntimeException {

    private final InvokeInstruction __insn;

    //

    public InvalidStaticContextInvocationException (
        final String message, final InvokeInstruction insn
    ) {
        super (message);
        __insn = insn;
    }

    public InvokeInstruction insn () {
        return __insn;
    }
}
