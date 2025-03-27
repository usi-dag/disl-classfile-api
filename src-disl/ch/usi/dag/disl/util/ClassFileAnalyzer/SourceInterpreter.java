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
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.*;
import java.lang.constant.ConstantDesc;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceInterpreter extends Interpreter<SourceValue> {


    @Override
    public SourceValue newValue(TypeKind type) {
        if (type == TypeKind.VOID) {
            return null;
        }
        return new SourceValue(
                type == null ? 1 : type.slotSize()
        );
    }

    @Override
    public SourceValue newOperation(Instruction instruction) throws AnalyzerException {
        int size;
        switch (instruction) {
            case ConstantInstruction constantInstruction -> {
                switch (constantInstruction.opcode()) {
                    case LCONST_0,
                         LCONST_1,
                         DCONST_0,
                         DCONST_1 -> size = 2;
                    case LDC,
                         LDC_W,
                         LDC2_W -> {
                        ConstantDesc value = constantInstruction.constantValue();
                        if (value instanceof Long || value instanceof Double ) {
                            size = 2;
//                        } else if (value instanceof ConstantDynamic) {
//                            // TODO since is a class related to asm should it really be included???
                        } else {
                            size = 1;
                        }
                    }
                    default -> size = 1;
                }
            }
            case FieldInstruction fieldInstruction -> {
                if (fieldInstruction.opcode() == Opcode.GETSTATIC) {
                    size = TypeKind.from(fieldInstruction.typeSymbol()).slotSize();
                } else {
                    size = 1;
                }
            }
            default -> size = 1;
        }

        return new SourceValue(size, instruction);
    }

    @Override
    public SourceValue copyOperation(Instruction instruction, SourceValue value) throws AnalyzerException {
        return new SourceValue(value.getSize(), instruction);
    }

    @Override
    public SourceValue unaryOperation(Instruction instruction, SourceValue value) throws AnalyzerException {
        int size;
        switch (instruction) {
            case FieldInstruction fieldInstruction -> {
                if (fieldInstruction.opcode() == Opcode.GETFIELD) {
                    size = TypeKind.from(fieldInstruction.typeSymbol()).slotSize();
                } else {
                    size = 1;
                }
            }
            case OperatorInstruction operatorInstruction -> {
                Opcode opcode = operatorInstruction.opcode();
                if (opcode == Opcode.LNEG || opcode == Opcode.DNEG) {
                    size = 2;
                } else {
                    size = 1;
                }
            }
            case ConvertInstruction convertInstruction -> {
                switch (convertInstruction.opcode()) {
                    case LNEG,
                         DNEG,
                         I2L,
                         I2D,
                         L2D,
                         F2L,
                         F2D,
                         D2L -> size = 2;
                    default -> size = 1;
                }
            }
            default -> size = 1;
        }
        return new SourceValue(size, instruction);
    }

    @Override
    public SourceValue binaryOperation(Instruction instruction, SourceValue value1, SourceValue value2) throws AnalyzerException {
        int size = switch (instruction.opcode()) {
            case LALOAD, DALOAD, LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM, LSHL, LSHR, LUSHR, LAND,
                 LOR, LXOR -> 2;
            default -> 1;
        };
        return new SourceValue(size, instruction);
    }

    @Override
    public SourceValue ternaryOperation(Instruction instruction, SourceValue value1, SourceValue value2, SourceValue value3) throws AnalyzerException {
        return new SourceValue(1, instruction);
    }

    @Override
    public void returnOperation(Instruction insn, SourceValue value, SourceValue expected) throws AnalyzerException {
        // Nothing to do.
    }

    @Override
    public SourceValue merge(SourceValue value1, SourceValue value2) {
        if (value1.size != value2.size || !containsAll(value1.instructions, value2.instructions)) {
            Set<CodeElement> union = new HashSet<>();
            union.addAll(value1.instructions);
            union.addAll(value2.instructions);
            return new SourceValue(Math.min(value1.size, value2.size), union);
        }
        return value1;
    }

    @Override
    public SourceValue naryOperation(Instruction instruction, List<? extends SourceValue> values) throws AnalyzerException {
        int size;
        switch (instruction) {
            case NewMultiArrayInstruction _ -> size = 1;
            case InvokeDynamicInstruction invokeDynamicInstruction -> size = TypeKind.from(invokeDynamicInstruction.typeSymbol().returnType()).slotSize();
            case InvokeInstruction invokeInstruction -> size = TypeKind.from(invokeInstruction.typeSymbol().returnType()).slotSize();
            default -> throw new AnalyzerException(instruction, "Invalid nary Operation for SourceValue");
        }
        return new SourceValue(size, instruction);
    }

    public static <E> boolean containsAll(final Set<E> self, final Set<E> other) {
        if (self.size() < other.size()) {
            return false;
        }
        return self.containsAll(other);
    }
}
