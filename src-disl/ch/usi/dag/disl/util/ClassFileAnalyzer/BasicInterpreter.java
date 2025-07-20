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
import java.lang.classfile.Instruction;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.util.List;

/**
 * An {@link Interpreter} for {@link BasicValue} values.
 *
 * @author Eric Bruneton
 * @author Bing Ran
 */
public class BasicInterpreter extends Interpreter<BasicValue> {


    /**
     * Special type used for the {@literal null} literal. This is an object reference type with
     * descriptor 'Lnull;'.
     */
    public static final TypeKind NULL_TYPE = TypeKind.from(ClassDesc.ofDescriptor("Lnull;"));

    public BasicInterpreter() {
        // TODO what is this for???
        if (getClass() != BasicInterpreter.class) {
            throw new IllegalStateException();
        }
    }

    @Override
    public BasicValue newValue(final TypeKind typeKind) {
        if (typeKind == null) {
            return BasicValue.UNINITIALIZED_VALUE;
        }
        switch (typeKind) {
            case VOID -> {
                return null;
            }
            case BOOLEAN,
                 CHAR,
                 BYTE,
                 SHORT,
                 INT -> {
                return BasicValue.INT_VALUE;
            }
            case FLOAT -> {
                return BasicValue.FLOAT_VALUE;
            }
            case LONG -> {
                return BasicValue.LONG_VALUE;
            }
            case DOUBLE -> {
                return BasicValue.DOUBLE_VALUE;
            }
            case REFERENCE -> {
                return BasicValue.REFERENCE_VALUE;
            }
            default -> {
                throw new AssertionError();
            }
        }
    }

    @Override
    public BasicValue newOperation(Instruction instruction) throws AnalyzerException {
        switch (instruction.opcode()) {
            case ACONST_NULL:
                return newValue(NULL_TYPE);
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5, BIPUSH, SIPUSH:
                return BasicValue.INT_VALUE;
            case LCONST_0:
            case LCONST_1:
                return BasicValue.LONG_VALUE;
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
                return BasicValue.FLOAT_VALUE;
            case DCONST_0:
            case DCONST_1:
                return BasicValue.DOUBLE_VALUE;
            case LDC:
            case LDC_W:
            case LDC2_W:
                ConstantInstruction.LoadConstantInstruction constantInstruction = (ConstantInstruction.LoadConstantInstruction) instruction;
                TypeKind typeKind = constantInstruction.typeKind();
                try {
                    return newValue(typeKind);
                } catch (Exception e) {
                    throw new AnalyzerException(constantInstruction, "Illegal LDC value " + constantInstruction.constantValue());
                }
            case JSR:
                return BasicValue.RETURNADDRESS_VALUE;
            case GETSTATIC:
                FieldInstruction fieldInstruction = (FieldInstruction) instruction;
                TypeKind fieldDesc = TypeKind.from(fieldInstruction.typeSymbol());
                return newValue(fieldDesc);
            case NEW:
                NewObjectInstruction newObjectInstruction = (NewObjectInstruction) instruction;
                TypeKind objDesc = TypeKind.from(newObjectInstruction.getClass());
                return newValue(objDesc);
            default:
                throw new AssertionError("Invalid Opcode: " + instruction.opcode() + " in BasicInterpreter.newOperation(Instruction instruction)");
        }
    }

    @Override
    public BasicValue copyOperation(Instruction insn, BasicValue value) throws AnalyzerException {
        return value;
    }

    @Override
    public BasicValue unaryOperation(Instruction instruction, BasicValue value) throws AnalyzerException {
        switch (instruction.opcode()) {
            case INEG:
            case IINC:
            case L2I:
            case F2I:
            case D2I:
            case I2B:
            case I2C:
            case I2S, ARRAYLENGTH, INSTANCEOF:
                return BasicValue.INT_VALUE;
            case FNEG:
            case I2F:
            case L2F:
            case D2F:
                return BasicValue.FLOAT_VALUE;
            case LNEG:
            case I2L:
            case F2L:
            case D2L:
                return BasicValue.LONG_VALUE;
            case DNEG:
            case I2D:
            case L2D:
            case F2D:
                return BasicValue.DOUBLE_VALUE;
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case ARETURN:
            case PUTSTATIC, ATHROW, MONITORENTER, MONITOREXIT, IFNULL, IFNONNULL:
                return null;
            case GETFIELD:
                FieldInstruction fieldInstruction = (FieldInstruction) instruction;
                return newValue(TypeKind.from(fieldInstruction.typeSymbol()));
            case NEWARRAY, ANEWARRAY:
                return BasicValue.REFERENCE_VALUE;
            case CHECKCAST:
                TypeCheckInstruction typeCheckInstruction = (TypeCheckInstruction) instruction;
                return newValue(typeCheckInstruction.type().typeKind());
            default:
                throw new AssertionError();
        }
    }


    @Override
    public BasicValue binaryOperation(Instruction insn, BasicValue value1, BasicValue value2) throws AnalyzerException {
        switch (insn.opcode()) {
            case IALOAD:
            case BALOAD:
            case CALOAD:
            case SALOAD:
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case ISHL:
            case ISHR:
            case IUSHR:
            case IAND:
            case IOR:
            case IXOR, LCMP, FCMPL, FCMPG, DCMPL, DCMPG:
                return BasicValue.INT_VALUE;
            case FALOAD:
            case FADD:
            case FSUB:
            case FMUL:
            case FDIV:
            case FREM:
                return BasicValue.FLOAT_VALUE;
            case LALOAD:
            case LADD:
            case LSUB:
            case LMUL:
            case LDIV:
            case LREM:
            case LSHL:
            case LSHR:
            case LUSHR:
            case LAND:
            case LOR:
            case LXOR:
                return BasicValue.LONG_VALUE;
            case DALOAD:
            case DADD:
            case DSUB:
            case DMUL:
            case DDIV:
            case DREM:
                return BasicValue.DOUBLE_VALUE;
            case AALOAD:
                return BasicValue.REFERENCE_VALUE;
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case PUTFIELD:
                return null;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public BasicValue ternaryOperation(Instruction insn, BasicValue value1, BasicValue value2, BasicValue value3) throws AnalyzerException {
        return null;
    }


    @Override
    public BasicValue naryOperation(Instruction insn, List<? extends BasicValue> values) throws AnalyzerException {
        switch (insn) {
            case NewMultiArrayInstruction newMultiArrayInstruction -> {
                return newValue(newMultiArrayInstruction.arrayType().typeKind());
            }
            case InvokeDynamicInstruction invokeDynamicInstruction -> {
                return newValue(TypeKind.from(invokeDynamicInstruction.typeSymbol().returnType()));
            }
            case InvokeInstruction invokeInstruction -> {
                return newValue(TypeKind.from(invokeInstruction.typeSymbol().returnType()));

            }
            default -> {
                throw new AnalyzerException(insn, "Invalid unary Operation");
            }
        }
    }

    @Override
    public void returnOperation(Instruction insn, BasicValue value, BasicValue expected) throws AnalyzerException {
        // Nothing to do.
    }

    @Override
    public BasicValue merge(BasicValue value1, BasicValue value2) {
        if (!value1.equals(value2)) {
            return BasicValue.UNINITIALIZED_VALUE;
        }
        return value1;
    }

}