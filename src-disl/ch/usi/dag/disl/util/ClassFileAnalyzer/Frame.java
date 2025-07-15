package ch.usi.dag.disl.util.ClassFileAnalyzer;
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.


// this code was ported from ASM, but is modified to work with the Java CLass File API
import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

public class Frame<V extends Value> {

    /** The maximum size of the operand stack of any method. */
    private static final int MAX_STACK_SIZE = 65536;

    /** The expected return type of the analyzed method, or {@literal null} if the method returns void. */
    private V returnValue;

    /**
     * The local variables and the operand stack of this frame. The first {@link #numLocals} elements
     * correspond to the local variables. The following {@link #numStack} elements correspond to the
     * operand stack. Long and double values are represented with two elements in the local variables
     * section, and with one element in the operand stack section.
     */
    private V[] values;

    /** The number of local variables of this frame. Long and double values are represented with two elements. */
    private int numLocals;

    /** The number of elements in the operand stack. Long and double values are represented with a single element. */
    private int numStack;

    /** The maximum number of elements in the operand stack. Long and double values are represented with a single element. */
    private int maxStack;

    /**
     * Constructs a new frame with the given size.
     *
     * @param numLocals the number of local variables of the frame. Long and double values are
     *     represented with two elements.
     * @param maxStack the maximum number of elements in the operand stack, or -1 if there is no
     *     maximum value. Long and double values are represented with a single element.
     */
    @SuppressWarnings("unchecked")
    public Frame(final int numLocals, final int maxStack) {
        this.values = (V[]) new Value[numLocals + (maxStack >= 0 ? maxStack : 4)];
        this.numLocals = numLocals;
        this.numStack = 0;
        this.maxStack = maxStack >= 0 ? maxStack : MAX_STACK_SIZE;
    }

    /**
     * Constructs a copy of the given Frame.
     *
     * @param frame a frame.
     */
    public Frame(final Frame<? extends V> frame) {
        this(frame.numLocals, frame.values.length - frame.numLocals);
        init(frame);
    }

    /**
     * Copies the state of the given frame into this frame.
     *
     * @param frame a frame.
     * @return this frame.
     */
    public Frame<V> init(final Frame<? extends V> frame) {
        returnValue = frame.returnValue;
        if (values.length < frame.values.length) {
            values = frame.values.clone();
        } else {
            System.arraycopy(frame.values, 0, values, 0, frame.values.length);
        }
        numLocals = frame.numLocals;
        numStack = frame.numStack;
        maxStack = frame.maxStack;
        return this;
    }

    /**
     * Initializes a frame corresponding to the target or to the successor of a jump instruction. This
     * method is called by {@link Analyzer#analyze(ClassDesc, List, List, AccessFlags, int, int, MethodTypeDesc)} while
     * interpreting jump instructions. It is called once for each possible target of the jump
     * instruction, and once for its successor instruction (except for GOTO and JSR), before the frame
     * is merged with the existing frame at this location. The default implementation of this method
     * does nothing.
     *
     * <p>Overriding this method and changing the frame values allows implementing branch-sensitive
     * analyses.
     *
     * @param opcode the opcode of the jump instruction. Can be IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE,
     *     IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE,
     *     GOTO, JSR, IFNULL, IFNONNULL, TABLESWITCH or LOOKUPSWITCH.
     * @param target a target of the jump instruction this frame corresponds to, or {@literal null} if
     *     this frame corresponds to the successor of the jump instruction (i.e. the next instruction
     *     in the instructions sequence).
     */
    public void initJumpTarget(final int opcode, final Label target) {
        // Does nothing by default.
    }

    // TODO alternative that use opcode, instead of int. need to see which is better
    public void initJumpTarget(final Opcode opcode, final Label target) {
        // Does nothing by default.
    }

    /**
     * Sets the expected return type of the analyzed method.
     *
     * @param v the expected return type of the analyzed method, or {@literal null} if the method
     *     returns void.
     */
    public void setReturn(final V v) {
        returnValue = v;
    }

    /**
     * Returns the maximum number of local variables of this frame. Long and double values are
     * represented with two variables.
     *
     * @return the maximum number of local variables of this frame.
     */
    public int getLocals() {
        return numLocals;
    }

    /**
     * Returns the maximum number of elements in the operand stack of this frame. Long and double
     * values are represented with a single element.
     *
     * @return the maximum number of elements in the operand stack of this frame.
     */
    public int getMaxStackSize() {
        return maxStack;
    }

    /**
     * Returns the value of the given local variable. Long and double values are represented with two
     * variables.
     *
     * @param index a local variable index.
     * @return the value of the given local variable.
     * @throws IndexOutOfBoundsException if the variable does not exist.
     */
    public V getLocal(final int index) {
        if (index >= numLocals) {
            throw new IndexOutOfBoundsException("Trying to get an inexistant local variable " + index);
        }
        return values[index];
    }

    /**
     * Sets the value of the given local variable. Long and double values are represented with two
     * variables.
     *
     * @param index a local variable index.
     * @param value the new value of this local variable.
     * @throws IndexOutOfBoundsException if the variable does not exist.
     */
    public void setLocal(final int index, final V value) {
        if (index >= numLocals) {
            throw new IndexOutOfBoundsException("Trying to set an inexistant local variable " + index);
        }
        values[index] = value;
    }

    /**
     * Returns the number of elements in the operand stack of this frame. Long and double values are
     * represented with a single element.
     *
     * @return the number of elements in the operand stack of this frame.
     */
    public int getStackSize() {
        return numStack;
    }

    /**
     * Returns the value of the given operand stack slot.
     *
     * @param index the index of an operand stack slot.
     * @return the value of the given operand stack slot.
     * @throws IndexOutOfBoundsException if the operand stack slot does not exist.
     */
    public V getStack(final int index) {
        return values[numLocals + index];
    }

    /**
     * Sets the value of the given stack slot.
     *
     * @param index the index of an operand stack slot.
     * @param value the new value of the stack slot.
     * @throws IndexOutOfBoundsException if the stack slot does not exist.
     */
    public void setStack(final int index, final V value) {
        values[numLocals + index] = value;
    }

    /** Clears the operand stack of this frame. */
    public void clearStack() {
        numStack = 0;
    }

    /**
     * Pops a value from the operand stack of this frame.
     *
     * @return the value that has been popped from the stack.
     * @throws IndexOutOfBoundsException if the operand stack is empty.
     */
    public V pop() {
        if (numStack == 0) {
            throw new IndexOutOfBoundsException("Cannot pop operand off an empty stack.");
        }
        return values[numLocals + (--numStack)];
    }

    /**
     * Pushes a value into the operand stack of this frame.
     *
     * @param value the value that must be pushed into the stack.
     * @throws IndexOutOfBoundsException if the operand stack is full.
     */
    @SuppressWarnings("unchecked")
    public void push(final V value) {
        if (numLocals + numStack >= values.length) {
            if (numLocals + numStack >= maxStack) {
                throw new IndexOutOfBoundsException("Insufficient maximum stack size. Exception generated by method Frame.push(V value)\n" +
                "numLocals (" + numLocals + "), numStack (" + numStack + "), maxStack (" + maxStack + "), value: " + value.toString());
            }
            V[] oldValues = values;
            values = (V[]) new Value[2 * values.length];
            System.arraycopy(oldValues, 0, values, 0, oldValues.length);
        }
        values[numLocals + (numStack++)] = value;
    }

    /**
     * Merges the given frame into this frame.
     *
     * @param frame a frame. This frame is left unchanged by this method.
     * @param interpreter the interpreter used to merge values.
     * @return {@literal true} if this frame has been changed as a result of the merge operation, or
     *     {@literal false} otherwise.
     * @throws AnalyzerException if the frames have incompatible sizes.
     */
    public boolean merge(final Frame<? extends V> frame, final Interpreter<V> interpreter)
            throws AnalyzerException {
        if (numStack != frame.numStack) {
            throw new AnalyzerException(null, "Incompatible stack heights");
        }
        boolean changed = false;
        for (int i = 0; i < numLocals + numStack; ++i) {
            V v = interpreter.merge(values[i], frame.values[i]);
            if (!v.equals(values[i])) {
                values[i] = v;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Merges the given frame into this frame (case of a subroutine). The operand stacks are not
     * merged, and only the local variables that have not been used by the subroutine are merged.
     *
     * @param frame a frame. This frame is left unchanged by this method.
     * @param localsUsed the local variables that are read or written by the subroutine. The i-th
     *     element is true if and only if the local variable at index i is read or written by the
     *     subroutine.
     * @return {@literal true} if this frame has been changed as a result of the merge operation, or
     *     {@literal false} otherwise.
     */
    public boolean merge(final Frame<? extends V> frame, final boolean[] localsUsed) {
        boolean changed = false;
        for (int i = 0; i < numLocals; ++i) {
            if (!localsUsed[i] && !values[i].equals(frame.values[i])) {
                values[i] = frame.values[i];
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Simulates the execution of the given instruction on this execution stack frame.
     *
     * @param instruction the instruction to execute.
     * @param interpreter the interpreter to use to compute values from other values.
     * @throws AnalyzerException if the instruction cannot be executed on this execution frame (e.g. a
     *     POP on an empty operand stack).
     */
    public void execute(final Instruction instruction, final Interpreter<V> interpreter)
            throws AnalyzerException {
        V value1;
        V value2;
        V value3;
        V value4;
        int varIndex;

        switch (instruction) {
            case NopInstruction ignored -> {}
            case ConstantInstruction constantInstruction ->
                    push(interpreter.newOperation(constantInstruction));
            case LoadInstruction loadInstruction ->
                    push(interpreter.copyOperation(loadInstruction, getLocal(loadInstruction.slot())));
            case StoreInstruction storeInstruction -> {
                value1 = interpreter.copyOperation(storeInstruction, pop());
                varIndex = storeInstruction.slot();
                setLocal(varIndex, value1);
                if (value1.getSize() == 2) {
                    setLocal(varIndex + 1, interpreter.newEmptyValue(varIndex + 1));
                }
                if (varIndex > 0) {
                    Value local = getLocal(varIndex - 1);
                    if (local != null && local.getSize() == 2) {
                        setLocal(varIndex - 1, interpreter.newEmptyValue(varIndex - 1));
                    }
                }
            }
            case ArrayStoreInstruction arrayStoreInstruction -> {
                value3 = pop();
                value2 = pop();
                value1 = pop();
                interpreter.ternaryOperation(arrayStoreInstruction, value1, value2, value3);
            }
            case StackInstruction stackInstruction -> {
                switch (stackInstruction.opcode()) {
                    case POP -> {
                        if (pop().getSize() == 2) {
                            throw new AnalyzerException(stackInstruction, "Illegal use of POP");
                        }
                    }
                    case POP2 -> {
                        if (pop().getSize() == 1 && pop().getSize() != 1) {
                            throw new AnalyzerException(stackInstruction, "Illegal use of POP2");
                        }
                    }
                    case DUP -> {
                        value1 = pop();
                        if (value1.getSize() != 1) {
                            throw new AnalyzerException(stackInstruction, "Illegal use of DUP");
                        }
                        push(interpreter.copyOperation(stackInstruction, value1));
                        push(interpreter.copyOperation(stackInstruction, value1));
                    }
                    case DUP_X1 -> {
                        value1 = pop();
                        value2 = pop();
                        if (value1.getSize() != 1 || value2.getSize() != 1) {
                            throw new AnalyzerException(stackInstruction, "Illegal use of DUP_X1");
                        }
                        push(interpreter.copyOperation(stackInstruction, value1));
                        push(interpreter.copyOperation(stackInstruction, value2));
                        push(interpreter.copyOperation(stackInstruction, value1));
                    }
                    case DUP_X2 -> {
                        value1 = pop();
                        if (value1.getSize() == 1 && executeDupX2(stackInstruction, value1, interpreter)) {
                            break;
                        }
                        throw new AnalyzerException(stackInstruction, "Illegal use of DUP_X2");
                    }
                    case DUP2 -> {
                        value1 = pop();
                        if (value1.getSize() == 1) {
                            value2 = pop();
                            if (value2.getSize() == 1) {
                                push(interpreter.copyOperation(stackInstruction, value2));
                                push(interpreter.copyOperation(stackInstruction, value1));
                                push(interpreter.copyOperation(stackInstruction, value2));
                                push(interpreter.copyOperation(stackInstruction, value1));
                                break;
                            }
                        } else {
                            push(interpreter.copyOperation(stackInstruction, value1));
                            push(interpreter.copyOperation(stackInstruction, value1));
                            break;
                        }
                        throw new AnalyzerException(stackInstruction, "Illegal use of DUP2");
                    }
                    case DUP2_X1 -> {
                        value1 = pop();
                        if (value1.getSize() == 1) {
                            value2 = pop();
                            if (value2.getSize() == 1) {
                                value3 = pop();
                                if (value3.getSize() == 1) {
                                    push(interpreter.copyOperation(stackInstruction, value2));
                                    push(interpreter.copyOperation(stackInstruction, value1));
                                    push(interpreter.copyOperation(stackInstruction, value3));
                                    push(interpreter.copyOperation(stackInstruction, value2));
                                    push(interpreter.copyOperation(stackInstruction, value1));
                                    break;
                                }
                            }
                        } else {
                            value2 = pop();
                            if (value2.getSize() == 1) {
                                push(interpreter.copyOperation(stackInstruction, value1));
                                push(interpreter.copyOperation(stackInstruction, value2));
                                push(interpreter.copyOperation(stackInstruction, value1));
                                break;
                            }
                        }
                        throw new AnalyzerException(stackInstruction, "Illegal use of DUP2_X1");
                    }
                    case DUP2_X2 -> {
                        value1 = pop();
                        if (value1.getSize() == 1) {
                            value2 = pop();
                            if (value2.getSize() == 1) {
                                value3 = pop();
                                if (value3.getSize() == 1) {
                                    value4 = pop();
                                    if (value4.getSize() == 1) {
                                        push(interpreter.copyOperation(stackInstruction, value2));
                                        push(interpreter.copyOperation(stackInstruction, value1));
                                        push(interpreter.copyOperation(stackInstruction, value4));
                                        push(interpreter.copyOperation(stackInstruction, value3));
                                        push(interpreter.copyOperation(stackInstruction, value2));
                                        push(interpreter.copyOperation(stackInstruction, value1));
                                        break;
                                    }
                                } else {
                                    push(interpreter.copyOperation(stackInstruction, value2));
                                    push(interpreter.copyOperation(stackInstruction, value1));
                                    push(interpreter.copyOperation(stackInstruction, value3));
                                    push(interpreter.copyOperation(stackInstruction, value2));
                                    push(interpreter.copyOperation(stackInstruction, value1));
                                    break;
                                }
                            }
                        } else if (executeDupX2(stackInstruction, value1, interpreter)) {
                            break;
                        }
                        throw new AnalyzerException(stackInstruction, "Illegal use of DUP2_X2");
                    }
                    case SWAP -> {
                        value2 = pop();
                        value1 = pop();
                        if (value1.getSize() != 1 || value2.getSize() != 1) {
                            throw new AnalyzerException(stackInstruction, "Illegal use of SWAP");
                        }
                        push(interpreter.copyOperation(stackInstruction, value2));
                        push(interpreter.copyOperation(stackInstruction, value1));
                    }
                }
            }
            case ArrayLoadInstruction arrayLoadInstruction -> {
                value2 = pop();
                value1 = pop();
                push(interpreter.binaryOperation(arrayLoadInstruction, value1, value2));
            }
            case OperatorInstruction operatorInstruction -> {
                switch (operatorInstruction.opcode()) {
                    case INEG,
                         LNEG,
                         FNEG,
                         DNEG,
                         ARRAYLENGTH -> {
                        push(interpreter.unaryOperation(operatorInstruction, pop()));
                    }
                    default -> {
                        value2 = pop();
                        value1 = pop();
                        push(interpreter.binaryOperation(operatorInstruction, value1, value2));
                    }

                }
            }
            case IncrementInstruction incrementInstruction -> {
                varIndex = incrementInstruction.slot();
                setLocal(varIndex, interpreter.unaryOperation(incrementInstruction, getLocal(varIndex)));

            }
            case ConvertInstruction convertInstruction -> {
                push(interpreter.unaryOperation(convertInstruction, pop()));
            }
            case BranchInstruction branchInstruction -> {
                switch (branchInstruction.opcode()) {
                    case IFEQ,
                         IFNE,
                         IFLT,
                         IFGE,
                         IFGT,
                         IFLE,
                         IFNULL,
                         IFNONNULL -> {
                        interpreter.unaryOperation(branchInstruction, pop());
                    }
                    case IF_ICMPEQ,
                         IF_ICMPNE,
                         IF_ICMPLT,
                         IF_ICMPGE,
                         IF_ICMPGT,
                         IF_ICMPLE,
                         IF_ACMPEQ,
                         IF_ACMPNE -> {
                        value2 = pop();
                        value1 = pop();
                        interpreter.binaryOperation(branchInstruction, value1, value2);
                    }
                    case GOTO,
                         GOTO_W -> {
                        // DO nothing
                    }
                }
            }
            case DiscontinuedInstruction discontinuedInstruction -> {
                switch (discontinuedInstruction.opcode()) {
                    case JSR,
                         JSR_W -> {
                        push(interpreter.newOperation(discontinuedInstruction));

                    }
                    case RET,
                         RET_W -> {
                        // do nothing
                    }
                }
            }
            case LookupSwitchInstruction lookupSwitchInstruction -> {
                interpreter.unaryOperation(lookupSwitchInstruction, pop());
            }
            case TableSwitchInstruction tableSwitchInstruction -> {
                interpreter.unaryOperation(tableSwitchInstruction, pop());
            }
            case ReturnInstruction returnInstruction -> {
                if (returnInstruction.opcode().equals(Opcode.RETURN)) {
                    if (returnValue != null) {
                        throw new AnalyzerException(returnInstruction, "Incompatible return type");
                    }
                    break;
                }
                value1 = pop();
                interpreter.unaryOperation(returnInstruction, value1);
                interpreter.returnOperation(returnInstruction, value1, returnValue);
            }
            case FieldInstruction fieldInstruction -> {
                switch (fieldInstruction.opcode()) {
                    case GETSTATIC -> {
                        push(interpreter.newOperation(fieldInstruction));
                    }
                    case PUTSTATIC -> {
                        interpreter.unaryOperation(fieldInstruction, pop());
                    }
                    case GETFIELD -> {
                        push(interpreter.unaryOperation(fieldInstruction, pop()));
                    }
                    case PUTFIELD -> {
                        value2 = pop();
                        value1 = pop();
                        interpreter.binaryOperation(fieldInstruction, value1, value2);
                    }
                }
            }
            case InvokeInstruction invokeInstruction -> {
                executeInvokeInsn(invokeInstruction, invokeInstruction.typeSymbol(), interpreter);
            }
            case InvokeDynamicInstruction invokeDynamicInstruction -> {
                executeInvokeInsn(invokeDynamicInstruction, invokeDynamicInstruction.typeSymbol(), interpreter);
            }
            case NewObjectInstruction newObjectInstruction -> {
                push(interpreter.newOperation(newObjectInstruction));
            }
            case NewPrimitiveArrayInstruction newPrimitiveArrayInstruction -> {
                push(interpreter.unaryOperation(newPrimitiveArrayInstruction, pop()));
            }
            case NewReferenceArrayInstruction newReferenceArrayInstruction -> {
                push(interpreter.unaryOperation(newReferenceArrayInstruction, pop()));
            }
            case ThrowInstruction throwInstruction -> {
                interpreter.unaryOperation(throwInstruction, pop());
            }
            case TypeCheckInstruction typeCheckInstruction -> {
                push(interpreter.unaryOperation(typeCheckInstruction, pop()));
            }
            case MonitorInstruction monitorInstruction -> {
                interpreter.unaryOperation(monitorInstruction, pop());
            }
            case NewMultiArrayInstruction newMultiArrayInstruction -> {
                List<V> valueList = new ArrayList<>();
                for (int i = newMultiArrayInstruction.dimensions(); i > 0; --i) {
                    valueList.addFirst(pop());
                }
                push(interpreter.naryOperation(newMultiArrayInstruction, valueList));
            }
            default -> throw new AnalyzerException(instruction, "illegal instruction with opcode " + instruction.opcode().toString());
        }
    }

    private void executeInvokeInsn(
            final Instruction instruction, final MethodTypeDesc methodDescriptor, final Interpreter<V> interpreter
            ) throws AnalyzerException {
        ArrayList<V> valueList = new ArrayList<>();
        for (int i = methodDescriptor.parameterCount(); i > 0; --i) {
            valueList.addFirst(pop());
        }
        if (instruction.opcode() != Opcode.INVOKESTATIC && instruction.opcode() != Opcode.INVOKEDYNAMIC) {
            valueList.addFirst(pop());
        }
        if (TypeKind.from(methodDescriptor.returnType()) == TypeKind.VOID) {
            interpreter.naryOperation(instruction, valueList);
        } else {
            push(interpreter.naryOperation(instruction, valueList));
        }
    }

    private boolean executeDupX2(
            final Instruction instruction, final V value1, final Interpreter<V> interpreter)
        throws AnalyzerException {
        V value2 = pop();
        if (value2.getSize() == 1) {
            V value3 = pop();
            if (value3.getSize() == 1) {
                push(interpreter.copyOperation(instruction, value1));
                push(interpreter.copyOperation(instruction, value3));
                push(interpreter.copyOperation(instruction, value2));
                push(interpreter.copyOperation(instruction, value1));
                return true;
            }
        } else {
            push(interpreter.copyOperation(instruction, value1));
            push(interpreter.copyOperation(instruction, value2));
            push(interpreter.copyOperation(instruction, value1));
            return true;
        }
        return false;
    }



    /**
     * Returns a string representation of this frame.
     *
     * @return a string representation of this frame.
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < getLocals(); ++i) {
            stringBuilder.append(getLocal(i));
        }
        stringBuilder.append(' ');
        for (int i = 0; i < getStackSize(); ++i) {
            stringBuilder.append(getStack(i).toString());
        }
        return stringBuilder.toString();
    }
}
