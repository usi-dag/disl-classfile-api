package ch.usi.dag.disl.weaver.peClassFile;

import ch.usi.dag.disl.util.ClassFileAnalyzer.AnalyzerException;
import ch.usi.dag.disl.util.ClassFileAnalyzer.Interpreter;

import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Objects;

public class ConstInterpreter extends Interpreter<ConstValue> {

    @Override
    public ConstValue newValue(TypeKind type) {
        if (type == TypeKind.VOID) {
            return null;
        }
        return new ConstValue(type == null ? 1 : type.slotSize());
    }

    @Override
    public ConstValue newOperation(Instruction instruction) throws AnalyzerException {
        switch (instruction.opcode()) {
            case Opcode.ACONST_NULL:
                return new ConstValue(1, ConstValue.NULL);
            case Opcode.ICONST_M1:
                return new ConstValue(1, (Integer) (-1));
            case Opcode.ICONST_0:
                return new ConstValue(1, (Integer) 0);
            case Opcode.ICONST_1:
                return new ConstValue(1, (Integer) 1);
            case Opcode.ICONST_2:
                return new ConstValue(1, (Integer) 2);
            case Opcode.ICONST_3:
                return new ConstValue(1, (Integer) 3);
            case Opcode.ICONST_4:
                return new ConstValue(1, (Integer) 4);
            case Opcode.ICONST_5:
                return new ConstValue(1, (Integer) 5);
            case Opcode.LCONST_0:
                return new ConstValue(2, 0L);
            case Opcode.LCONST_1:
                return new ConstValue(2, 1L);
            case Opcode.FCONST_0:
                return new ConstValue(1, (float) 0);
            case Opcode.FCONST_1:
                return new ConstValue(1, 1F);
            case Opcode.FCONST_2:
                return new ConstValue(1, 2F);
            case Opcode.DCONST_0:
                return new ConstValue(2, (double) 0);
            case Opcode.DCONST_1:
                return new ConstValue(2, 1.0);
            case Opcode.BIPUSH:
            case Opcode.SIPUSH:
                ConstantInstruction.ArgumentConstantInstruction ci = (ConstantInstruction.ArgumentConstantInstruction) instruction;
                return new ConstValue(1, ci.constantValue());
            case Opcode.LDC:
                ConstantInstruction.LoadConstantInstruction lci = (ConstantInstruction.LoadConstantInstruction) instruction;
                Object cst;
                try {
                    cst = lci.constantEntry().constantValue().resolveConstantDesc(MethodHandles.lookup());;
                } catch (ReflectiveOperationException e) {
                    throw new AnalyzerException(null, "Error while resolving constant!");
                }
                return new ConstValue(
                        cst instanceof Long || cst instanceof Double ? 2 : 1, cst);
            case Opcode.GETSTATIC:
                FieldInstruction fi = (FieldInstruction) instruction;
                return new ConstValue(TypeKind.from(fi.typeSymbol()).slotSize());
            case Opcode.NEW:
                return new ConstValue(1, new Reference());

            default:
                return new ConstValue(1);
        }
    }

    @Override
    public ConstValue copyOperation(Instruction insn, ConstValue value) throws AnalyzerException {
        return new ConstValue(value.getSize(), value.cst);
    }

    // TODO In the original version this static function was here in this file but why??? shouldn't it be in an helper function???
    public static boolean mightBeUnaryConstOperation(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case Opcode.INEG, Opcode.LNEG, Opcode.FNEG, Opcode.DNEG, Opcode.IINC, Opcode.I2L, Opcode.I2F, Opcode.I2D,
                 Opcode.L2I, Opcode.L2F, Opcode.L2D, Opcode.F2I, Opcode.F2L, Opcode.F2D, Opcode.D2I, Opcode.D2L,
                 Opcode.D2F, Opcode.I2B, Opcode.I2C, Opcode.I2S, Opcode.CHECKCAST, Opcode.INSTANCEOF -> true;
            default -> false;
        };
    }

    @Override
    public ConstValue unaryOperation(Instruction instruction, ConstValue value) throws AnalyzerException {
       if (value.cst == null) {
           return switch (instruction.opcode()) {
               case Opcode.LNEG, Opcode.DNEG, Opcode.I2L, Opcode.I2D, Opcode.L2D, Opcode.F2L, Opcode.F2D, Opcode.D2L ->
                       new ConstValue(2);
               case Opcode.GETFIELD -> {
                   FieldInstruction fi = (FieldInstruction) instruction;
                   yield new ConstValue(TypeKind.from(fi.typeSymbol()).slotSize());
               }
               default -> new ConstValue(1);
           };
       }
       
        switch (instruction.opcode()) {
            case Opcode.INEG:
                return new ConstValue(1, -(Integer) value.cst);

            case Opcode.LNEG:
                return new ConstValue(2, -(Long) value.cst);

            case Opcode.FNEG:
                return new ConstValue(1, -(Float) value.cst);

            case Opcode.DNEG, Opcode.F2D:
                return new ConstValue(2, -(Double) value.cst);

            case Opcode.IINC:
                IncrementInstruction inc = (IncrementInstruction) instruction;
                return new ConstValue(
                        1,
                        (Integer) value.cst + inc.constant());

            case Opcode.I2L:
                return new ConstValue(2, (long) ((Integer) value.cst));

            case Opcode.I2F:
                return new ConstValue(1, (float) ((Integer) value.cst));

            case Opcode.I2D:
                return new ConstValue(2, (double) ((Integer) value.cst));

            case Opcode.L2I:
                return new ConstValue(1,
                        (int) (long) ((Long) value.cst));

            case Opcode.L2F:
                return new ConstValue(1, (float) ((Long) value.cst));

            case Opcode.L2D:
                return new ConstValue(2, (double) ((Long) value.cst));

            case Opcode.F2I:
                return new ConstValue(1,
                        (int) ((float) ((Float) value.cst)));

            case Opcode.F2L:
                return new ConstValue(2,
                        (long) ((float) ((Float) value.cst)));

            case Opcode.D2I:
                return new ConstValue(1,
                        (int) (double) ((Double) value.cst));

            case Opcode.D2L:
                return new ConstValue(2,
                        (long) (double) ((Double) value.cst));

            case Opcode.D2F:
                return new ConstValue(1,
                        (float) (double) ((Double) value.cst));

            case Opcode.I2B:
                return new ConstValue(1,
                        (byte) (int) ((Integer) value.cst));

            case Opcode.I2C:
                return new ConstValue(1,
                        (char) (int) ((Integer) value.cst));

            case Opcode.I2S:
                return new ConstValue(1,
                        (short) (int) ((Integer) value.cst));

            case Opcode.IFEQ:
                return new ConstValue(1, (Integer) value.cst == 0);

            case Opcode.IFNE:
                return new ConstValue(1, (Integer) value.cst != 0);

            case Opcode.IFLT:
                return new ConstValue(1, (Integer) value.cst < 0);

            case Opcode.IFGE:
                return new ConstValue(1, (Integer) value.cst >= 0);

            case Opcode.IFGT:
                return new ConstValue(1, (Integer) value.cst > 0);

            case Opcode.IFLE:
                return new ConstValue(1, (Integer) value.cst <= 0);

            case Opcode.IFNULL:
                return new ConstValue(1, value.cst == ConstValue.NULL);

            case Opcode.IFNONNULL:
                return new ConstValue(1, value.cst != ConstValue.NULL);

            case Opcode.CHECKCAST:
                return new ConstValue(1, value.cst);

            case Opcode.INSTANCEOF:
                TypeCheckInstruction tci = (TypeCheckInstruction) instruction;
                Class<?> clazz = value.cst.getClass();
                ClassDesc checkType = tci.type().asSymbol();
                while (clazz != null) {

                    if (Objects.equals(checkType.descriptorString(), clazz.descriptorString())) {
                        return new ConstValue(1, 1);
                    }
                    clazz = clazz.getSuperclass();
                }

                return new ConstValue(1, 0);

            default:
                return new ConstValue(1);
       }
    }

    // TODO this might be moved to an helper function
    public static boolean mightBeBinaryConstOperation(
            final Instruction instruction) {

        return switch (instruction.opcode()) {
            case Opcode.LADD, Opcode.LSUB, Opcode.LMUL, Opcode.LDIV, Opcode.LREM, Opcode.LSHL, Opcode.LSHR,
                 Opcode.LUSHR, Opcode.LAND, Opcode.LOR, Opcode.LXOR, Opcode.DADD, Opcode.DSUB, Opcode.DMUL, Opcode.DDIV,
                 Opcode.DREM, Opcode.IADD, Opcode.ISUB, Opcode.IMUL, Opcode.IDIV, Opcode.IREM, Opcode.ISHL, Opcode.ISHR,
                 Opcode.IUSHR, Opcode.IAND, Opcode.IOR, Opcode.IXOR, Opcode.FADD, Opcode.FSUB, Opcode.FMUL, Opcode.FDIV,
                 Opcode.FREM, Opcode.LCMP, Opcode.FCMPL, Opcode.FCMPG, Opcode.DCMPL, Opcode.DCMPG -> true;
            default -> false;
        };
    }

    @Override
    public ConstValue binaryOperation(Instruction instruction, ConstValue value1, ConstValue value2) throws AnalyzerException {
        if (value1.cst == null || value2.cst == null) {
            return switch (instruction.opcode()) {
                case Opcode.LALOAD, Opcode.DALOAD, Opcode.LADD, Opcode.DADD, Opcode.LSUB, Opcode.DSUB, Opcode.LMUL,
                     Opcode.DMUL, Opcode.LDIV, Opcode.DDIV, Opcode.LREM, Opcode.DREM, Opcode.LSHL, Opcode.LSHR,
                     Opcode.LUSHR, Opcode.LAND, Opcode.LOR, Opcode.LXOR -> new ConstValue(2);
                default -> new ConstValue(1);
            };
        }
        
        switch (instruction.opcode()) {
            case Opcode.LALOAD:
            case Opcode.DALOAD:
                return new ConstValue(2);

            case Opcode.LADD:
                return new ConstValue(2,
                        (Long) value1.cst + (Long) value2.cst);

            case Opcode.LSUB:
                return new ConstValue(2,
                        (Long) value1.cst - (Long) value2.cst);

            case Opcode.LMUL:
                return new ConstValue(2,
                        (Long) value1.cst * (Long) value2.cst);

            case Opcode.LDIV:
                return new ConstValue(2,
                        (Long) value1.cst / (Long) value2.cst);

            case Opcode.LREM:
                return new ConstValue(2,
                        (Long) value1.cst % (Long) value2.cst);

            case Opcode.LSHL:
                return new ConstValue(2,
                        (Long) value1.cst << (Integer) value2.cst);

            case Opcode.LSHR:
                return new ConstValue(2,
                        (Long) value1.cst >> (Integer) value2.cst);

            case Opcode.LUSHR:
                return new ConstValue(2,
                        (Long) value1.cst >>> (Integer) value2.cst);

            case Opcode.LAND:
                return new ConstValue(2,
                        (Long) value1.cst & (Long) value2.cst);

            case Opcode.LOR:
                return new ConstValue(2,
                        (Long) value1.cst | (Long) value2.cst);

            case Opcode.LXOR:
                return new ConstValue(2,
                        (Long) value1.cst ^ (Long) value2.cst);

            case Opcode.DADD:
                return new ConstValue(2,
                        (Double) value1.cst + (Double) value2.cst);

            case Opcode.DSUB:
                return new ConstValue(2,
                        (Double) value1.cst - (Double) value2.cst);

            case Opcode.DMUL:
                return new ConstValue(2,
                        (Double) value1.cst * (Double) value2.cst);

            case Opcode.DDIV:
                return new ConstValue(2,
                        (Double) value1.cst / (Double) value2.cst);

            case Opcode.DREM:
                return new ConstValue(2,
                        (Double) value1.cst % (Double) value2.cst);

            case Opcode.IADD:
                return new ConstValue(1,
                        (Integer) value1.cst + (Integer) value2.cst);

            case Opcode.ISUB:
                return new ConstValue(1,
                        (Integer) value1.cst - (Integer) value2.cst);

            case Opcode.IMUL:
                return new ConstValue(1,
                        (Integer) value1.cst * (Integer) value2.cst);

            case Opcode.IDIV:
                return new ConstValue(1,
                        (Integer) value1.cst / (Integer) value2.cst);

            case Opcode.IREM:
                return new ConstValue(1,
                        (Integer) value1.cst % (Integer) value2.cst);

            case Opcode.ISHL:
                return new ConstValue(1,
                        (Integer) value1.cst << (Integer) value2.cst);

            case Opcode.ISHR:
                return new ConstValue(1,
                        (Integer) value1.cst >> (Integer) value2.cst);

            case Opcode.IUSHR:
                return new ConstValue(1,
                        (Integer) value1.cst >>> (Integer) value2.cst);

            case Opcode.IAND:
                return new ConstValue(1,
                        (Integer) value1.cst & (Integer) value2.cst);

            case Opcode.IOR:
                return new ConstValue(1,
                        (Integer) value1.cst | (Integer) value2.cst);

            case Opcode.IXOR:
                return new ConstValue(1,
                        (Integer) value1.cst ^ (Integer) value2.cst);

            case Opcode.FADD:
                return new ConstValue(1,
                        (Float) value1.cst + (Float) value2.cst);

            case Opcode.FSUB:
                return new ConstValue(1,
                        (Float) value1.cst - (Float) value2.cst);

            case Opcode.FMUL:
                return new ConstValue(1,
                        (Float) value1.cst * (Float) value2.cst);

            case Opcode.FDIV:
                return new ConstValue(1,
                        (Float) value1.cst / (Float) value2.cst);

            case Opcode.FREM:
                return new ConstValue(1,
                        (Float) value1.cst % (Float) value2.cst);

            case Opcode.LCMP:
                if ((Long) value1.cst > (Long) value2.cst) {
                    return new ConstValue(1, 1);
                } else if ((Long) value1.cst < (Long) value2.cst) {
                    return new ConstValue(1, -1);
                } else {
                    return new ConstValue(1, 0);
                }

            case Opcode.FCMPL:
            case Opcode.FCMPG:
                if ((Float) value1.cst > (Float) value2.cst) {
                    return new ConstValue(1, 1);
                } else if ((Float) value1.cst < (Float) value2.cst) {
                    return new ConstValue(1, -1);
                } else {
                    return new ConstValue(1, 0);
                }

            case Opcode.DCMPL:
            case Opcode.DCMPG:
                if ((Double) value1.cst > (Double) value2.cst) {
                    return new ConstValue(1, 1);
                } else if ((Double) value1.cst < (Double) value2.cst) {
                    return new ConstValue(1, -1);
                } else {
                    return new ConstValue(1, 0);
                }

            case Opcode.IF_ICMPEQ:
                return new ConstValue(1, value1.cst.equals(value2.cst));

            case Opcode.IF_ICMPNE:
                return new ConstValue(1, !value1.cst.equals(value2.cst));

            case Opcode.IF_ICMPLT:
                return new ConstValue(1,
                        (Integer) value1.cst < (Integer) value2.cst);

            case Opcode.IF_ICMPGE:
                return new ConstValue(1,
                        (Integer) value1.cst >= (Integer) value2.cst);

            case Opcode.IF_ICMPGT:
                return new ConstValue(1,
                        (Integer) value1.cst > (Integer) value2.cst);

            case Opcode.IF_ICMPLE:
                return new ConstValue(1,
                        (Integer) value1.cst <= (Integer) value2.cst);

            case Opcode.IF_ACMPEQ:
                return new ConstValue(1, value1.cst == value2.cst);

            case Opcode.IF_ACMPNE:
                return new ConstValue(1, value1.cst != value2.cst);

            default:
                return new ConstValue(1);
        }
    }

    @Override
    public ConstValue ternaryOperation(Instruction insn, ConstValue value1, ConstValue value2, ConstValue value3) throws AnalyzerException {
        return new ConstValue(1);
    }

    @Override
    public void returnOperation(Instruction insn, ConstValue value, ConstValue expected) throws AnalyzerException {}

    @Override
    public ConstValue merge(ConstValue d, ConstValue w) {
         if (d.size == w.size && d.cst != null && d.cst.equals(w.cst)) {
            return d;
        }
        return new ConstValue(Math.min(d.size, w.size));
    }

    @Override
    public ConstValue naryOperation(Instruction instruction, List<? extends ConstValue> values) throws AnalyzerException {
        Opcode opcode = instruction.opcode();
        if (opcode == Opcode.MULTIANEWARRAY) {
            return new ConstValue(1);
        } else if (opcode == Opcode.INVOKEDYNAMIC) {
            InvokeDynamicInstruction idi = (InvokeDynamicInstruction) instruction;
            TypeKind returnType = TypeKind.from(idi.typeSymbol().returnType());
            return new ConstValue(returnType.slotSize());
        } else {
            InvokeInstruction invoke = (InvokeInstruction) instruction;
            TypeKind returnType = TypeKind.from(invoke.typeSymbol().returnType());

            Object cst = InvocationInterpreter.getInstance().execute(
                    invoke, values);
            return new ConstValue(returnType.slotSize(), cst);
        }
    }

    private static ConstInterpreter instance;

    public static ConstInterpreter getInstance() {
        if (instance == null) {
            instance = new ConstInterpreter();
        }
        return instance;
    }
}
