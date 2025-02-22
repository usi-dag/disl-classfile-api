package ch.usi.dag.disl.util;

import ch.usi.dag.disl.exception.DiSLFatalException;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.TypeDescriptor;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

public abstract class ClassFileHelper {
    // TODO this class tries to replicate the utility of AsmHelper
    // TODO once the method are confirmed to be correct add docstrings

    /**
     * Returns {@code true} if the given list of instructions contains any real
     * (non-pseudo) instruction node in the range between {@code start}
     * (inclusive) and {@code end} exclusive.
     */
    public static boolean offsetBefore(final List<CodeElement> instructions,
                                       final int start, final int end) {
        for (int i = start; i < end; i++) {
            if (instructions.get(i) instanceof Instruction) {
                return true;
            }
        }
        return false;
    }

    // return true if the value also need a constant entry in the entry pool to be
    // loaded. meaning it need the instruction ldc and X_CONST or XPUSH are not sufficient.
    public static boolean needConstantEntryForLoad(final Object value) {
        switch (value) {
            case Boolean _, Byte _, Short _ -> {return false;}
            case Integer i -> {
                return Short.MIN_VALUE <= i && i <= Short.MAX_VALUE;
            }
            case Float f -> {return f == 0 || f == 1 || f == 2;}
            case Long l -> {return l == 0 || l == 1;}
            case Double d -> {return d == 0 || d == 1;}
            default -> {return true;}
        }
    }

    // TODO need to check if this is equivalent tp the asmHelper version
    public static CodeElement loadConst(final Object value) {
        switch (value) {
            case Boolean b -> {
                return ConstantInstruction.ofIntrinsic(
                        b ? Opcode.ICONST_1 : Opcode.ICONST_0
                );
            }
            case Byte _, Short _, Integer _ -> {
                final int intValue = ((Number) value).intValue();
                if (intValue == -1) {
                    return ConstantInstruction.ofIntrinsic(Opcode.ICONST_M1);
                } else if (0 <= intValue && intValue <= 5) {
                    // the constructor of Opcode only accept strings
                    return ConstantInstruction.ofIntrinsic(Opcode.valueOf("ICONST_" + intValue));
                } else if (Byte.MIN_VALUE <= intValue && intValue <= Byte.MAX_VALUE) {
                    return ConstantInstruction.ofArgument(Opcode.BIPUSH, intValue);
                } else if (Short.MIN_VALUE <= intValue && intValue <= Short.MAX_VALUE) {
                    return ConstantInstruction.ofArgument(Opcode.SIPUSH, intValue);
                } else {
                    ConstantPoolBuilder constantPoolBuilder = ConstantPoolBuilder.of();
                    LoadableConstantEntry constantEntry = constantPoolBuilder.loadableConstantEntry(intValue);
                    return ConstantInstruction.ofLoad(Opcode.LDC, constantEntry);
                }
            }
            default -> {
                ConstantPoolBuilder constantPoolBuilder = ConstantPoolBuilder.of();
                ClassDesc classDesc = ClassDesc.ofDescriptor(value.getClass().descriptorString());
                LoadableConstantEntry constantEntry = constantPoolBuilder.loadableConstantEntry(classDesc);
                return ConstantInstruction.ofLoad(Opcode.LDC, constantEntry);
            }
        }
    }

    // TODO this seem that should work, but need an expert opinion
    public static String getStringConstOperand(final CodeElement codeElement) {
        if (codeElement instanceof ConstantInstruction.LoadConstantInstruction) {
            LoadableConstantEntry entry = ((ConstantInstruction.LoadConstantInstruction) codeElement).constantEntry();
            ConstantDesc constant = entry.constantValue();
            if (constant instanceof String) {
                return (String) constant;
            }
        }
        return null; // Not a String literal load instruction.
    }

    // TODO also this seem correct, but need another opinion
    public static <T extends Enum<T>> T getEnumConstOperand(
            final Class<T> enumType, final CodeElement codeElement
    ) {
        if (
                enumType.isEnum() &&
                codeElement instanceof FieldInstruction &&
                ((FieldInstruction) codeElement).opcode() == Opcode.GETSTATIC
        ) {
            final FieldInstruction fieldInstruction = (FieldInstruction) codeElement;

            if (enumType.descriptorString().equals(fieldInstruction.owner().asSymbol().descriptorString())) {
                final String expectedName = fieldInstruction.name().stringValue();
                for (final T value: enumType.getEnumConstants()) {
                    if (value.name().equals(expectedName)) {
                        return value;
                    }
                }
            }
        }
        return null;  // Not an Enum<T> const operand.
    }


    // TODO this seem correct, but not 100% sure
    public static Integer getIntConstantOperand(final CodeElement codeElement) {
        switch (codeElement) {
            // case like  bipush
            case ConstantInstruction.ArgumentConstantInstruction instruction -> {
                return instruction.constantValue();
            }
            // case like  iconst_0
            case ConstantInstruction.IntrinsicConstantInstruction instruction -> {
                ConstantDesc constant = instruction.constantValue();
                if (constant instanceof Integer) {
                    return (Integer) constant;
                }
            }
            //case like  ldc
            case ConstantInstruction.LoadConstantInstruction instruction -> {
                ConstantDesc constant = instruction.constantValue();
                if (constant instanceof Integer) {
                    return (Integer) constant;
                }
            }
            default -> {return null;}  // Not an integer literal load instruction.
        }
        return null;  // Not an integer literal load instruction.
    }


    // TODO this is probably correct, but is ok to return a ConstantDesc???
    public static ConstantDesc getTypeConstOperand(final CodeElement codeElement) {
        switch (codeElement) {
            case ConstantInstruction.LoadConstantInstruction instruction -> {
                return instruction.constantValue();
            }
            case FieldInstruction instruction -> {
                if (instruction.opcode() == Opcode.GETSTATIC) {
                    return instruction.owner().asSymbol();
                }
            }
            default -> {return null;}// Not a class literal load.
        }
        return null;// Not a class literal load.
    }


    public static LoadInstruction loadThis() {
        return loadObjectVar(0);
    }

    public static LoadInstruction loadObjectVar(final int slot) {
        return loadVar(TypeKind.ReferenceType, slot);
    }

    public static LoadInstruction loadVar(final TypeKind typeKind, final int slot) {
        return LoadInstruction.of(typeKind.asLoadable(), slot);
    }


    public static StoreInstruction storeObjectVar(final int slot) {
        return storeVar(TypeKind.ReferenceType, slot);
    }

    public static StoreInstruction storeVar(final TypeKind typeKind, final int slot) {
        return StoreInstruction.of(typeKind, slot);
    }

    public static ConstantInstruction.IntrinsicConstantInstruction loadNull() {
        return loadDefault(TypeKind.ReferenceType);
    }

    public static ConstantInstruction.IntrinsicConstantInstruction loadDefault(final TypeKind typeKind) {
        Opcode opcode;
        switch (typeKind) {
            case BooleanType, ByteType, CharType, IntType, ShortType -> {
                opcode = Opcode.ICONST_0;
            }
            case LongType -> opcode = Opcode.LCONST_0;
            case FloatType -> opcode = Opcode.FCONST_0;
            case DoubleType -> opcode = Opcode.DCONST_0;
            // this type is for both object and array
            case ReferenceType -> opcode = Opcode.ACONST_NULL;
            default -> {
                throw new DiSLFatalException(
                        "No default value for type: "+ typeKind.name()
                );
            }
        }
        return ConstantInstruction.ofIntrinsic(opcode);
    }

    public static TypeCheckInstruction checkCast(ClassDesc classDesc) {
        return TypeCheckInstruction.of(Opcode.CHECKCAST, classDesc);
    }

    private static FieldInstruction fieldOperation(
            final String ownerDesc, final String name, final String desc, final Opcode opcode
    ) {
        ConstantPoolBuilder constantPoolBuilder = ConstantPoolBuilder.of();
        ClassEntry classEntry = constantPoolBuilder.classEntry(ClassDesc.ofDescriptor(ownerDesc));
        return FieldInstruction.of(
                opcode,
                classEntry,
                constantPoolBuilder.utf8Entry(name),
                constantPoolBuilder.utf8Entry(desc)
        );
    }

    public static FieldInstruction getField(final String ownerDesc, final String name, final String desc) {
        return fieldOperation(ownerDesc, name, desc, Opcode.GETFIELD);
    }


    public static FieldInstruction putField(final String ownerDesc, final String name, final String desc) {
        return fieldOperation(ownerDesc, name, desc, Opcode.PUTFIELD);
    }

    public static FieldInstruction getStatic(final String ownerDesc, final String name, final String desc) {
        return fieldOperation(ownerDesc, name, desc, Opcode.GETSTATIC);
    }

    public static FieldInstruction getStatic(final Field field) {
        return getStatic(field.getClass().getName(), field.getName(), field.getClass().descriptorString());
    }

    public static FieldInstruction putStatic(final String ownerDesc, final String name, final String desc) {
        return fieldOperation(ownerDesc, name, desc, Opcode.PUTSTATIC);
    }

    public static BranchInstruction jumpTo(final Label target) {
        return BranchInstruction.of(Opcode.GOTO, target);
    }

    public static InvokeInstruction invokeInstruction(
            final String ownerDec, final String methodName, final String methodDesc, final Opcode opcode, final boolean isInterface
    ) {
        ConstantPoolBuilder constantPoolBuilder = ConstantPoolBuilder.of();
        ClassEntry entry = constantPoolBuilder.classEntry(ClassDesc.ofDescriptor(ownerDec));
        return InvokeInstruction.of(
                opcode,
                entry,
                constantPoolBuilder.utf8Entry(methodName),
                constantPoolBuilder.utf8Entry(methodDesc),
                isInterface
        );
    }

    // TODO there might be a better way to do this (without using ASM)
    private static MethodTypeDesc getMethodDescriptor(final Method method) {
        Class<?> returnType = method.getReturnType();
        Class<?>[] parameters = method.getParameterTypes();
        ClassDesc classDesc = ClassDesc.ofDescriptor(returnType.descriptorString());
        List<ClassDesc> parametersDesc = Stream.of(parameters).map(c -> ClassDesc.ofDescriptor(c.descriptorString())).toList();
        return MethodTypeDesc.of(classDesc, parametersDesc);
    }

    public static InvokeInstruction invokeStatic(final Method method) {
        String methodDesc = getMethodDescriptor(method).descriptorString();
        return invokeStatic(method.getDeclaringClass().descriptorString(), method.getName(), methodDesc);
    }

    public static InvokeInstruction invokeStatic(final String ownerDesc, final String methodName, final String methodDesc) {
        return invokeInstruction(ownerDesc, methodName, methodDesc, Opcode.INVOKESTATIC, false);
    }

    public static InvokeInstruction invokeVirtual(final Method method) {
        String methodDesc = getMethodDescriptor(method).descriptorString();
        return invokeVirtual(method.getDeclaringClass().descriptorString(), method.getName(), methodDesc);
    }

    public static InvokeInstruction invokeVirtual(final String ownerDesc, final String methodName, final String methodDesc) {
        return invokeInstruction(ownerDesc, methodName, methodDesc, Opcode.INVOKEVIRTUAL, false);
    }

    // TODO this seem correct, but might need to double check
    public static int getParameterSlot(final MethodModel methodModel, final int paramIndex) {
        MethodTypeDesc methodTypeDesc = methodModel.methodTypeSymbol();
        if (paramIndex >= methodTypeDesc.parameterCount()) {
            throw new DiSLFatalException ("parameter index out of bounds");
        }
        List<ClassDesc> parameters =  methodTypeDesc.parameterList();
        int slot = methodModel.flags().has(AccessFlag.STATIC)? 0 : 1;

        for (int i = 0; i < paramIndex; i++) {
            slot += TypeKind.from(parameters.get(i)).slotSize();
        }
        return slot;
    }

    public static int getParameterSlotCount(final MethodModel methodModel) {
        MethodTypeDesc methodTypeDesc = methodModel.methodTypeSymbol();
        List<ClassDesc> parameters =  methodTypeDesc.parameterList();
        int result = methodModel.flags().has(AccessFlag.STATIC)? 0 : 1;

        for (ClassDesc parameter: parameters) {
            result += TypeKind.from(parameter).slotSize();
        }
        return result;
    }

    // TODO in the classFile api all element are immutable and new elements are
    //  created use builders so instead of cloning other helper might be needed.
    //  So for now I did not included all the cloning method from AsmHelper.java

    // TODO Some of the instruction helper methods are included in the class
    //  InstructionWrapper.java also instruction in asm are double linked list
    //  while in the classFile they are independent and stored in a normal
    //  list, so different kind of helper functions might be needed


    public static List<Instruction> selectReal(List<CodeElement> instructions) {
        return instructions.stream()
                .filter(i -> i instanceof Instruction)
                .map(i -> (Instruction)i)
                .toList();
    }

    public static Instruction nextRealInstruction(List<CodeElement> instructions, CodeElement start) {
        int index = instructions.indexOf(start);
        if (index < 0) {
            return null;
        }
        while (index < instructions.size()) {
            index += 1;
            if (instructions.get(index) instanceof Instruction) {
                return (Instruction) instructions.get(index);
            }
        }
        return null;
    }

    public static Instruction previousRealInstruction(List<CodeElement> instructions, CodeElement start) {
        int index = instructions.indexOf(start);
        if (index < 0) {
            return null;
        }
        while (index > 0) {
            index -= 1;
            if (instructions.get(index) instanceof Instruction) {
                return (Instruction) instructions.get(index);
            }
        }
        return null;
    }

    public static CodeElement nextInstruction(List<CodeElement> instructions, CodeElement start) {
        int index = instructions.indexOf(start);
        if (index < 0 || index + 1 >= instructions.size()) {
            return null;
        }
        return instructions.get(index+1);
    }

    public static CodeElement previousInstruction(List<CodeElement> instructions, CodeElement start) {
        int index = instructions.indexOf(start);
        if (index <= 0) {
            return null;
        }
        return instructions.get(index -1);
    }

    // also accept ClassDesc and I believe ConstDesc too
    public static boolean isReferenceType(TypeDescriptor.OfField<?> desc) {
        return isReferenceType(TypeKind.from(desc));
    }

    // TODO this should be correct, but it should also give true to
    //  encapsulated primitive such as Integer
    public static boolean isReferenceType(TypeKind typeKind) {
        return typeKind.equals(TypeKind.ReferenceType);
    }

    public static boolean isStaticFieldAccess(final CodeElement codeElement) {
        if (codeElement instanceof FieldInstruction) {
            return ((FieldInstruction) codeElement).opcode() == Opcode.GETSTATIC ||
                    ((FieldInstruction) codeElement).opcode() == Opcode.PUTSTATIC;
        }
        return false;
    }

    public static boolean isReturn(final CodeElement codeElement) {
        return codeElement instanceof ReturnInstruction;
    }


    public static boolean isBranch(final CodeElement codeElement) {
        if (!(codeElement instanceof Instruction)) {
            return false;
        }
        // TODO should return be included????
        Instruction instruction = (Instruction) codeElement;
        return codeElement instanceof BranchInstruction ||
                codeElement instanceof LookupSwitchInstruction ||
                codeElement instanceof TableSwitchInstruction ||
                codeElement instanceof ReturnInstruction ||
                instruction.opcode() == Opcode.ATHROW ||
                instruction.opcode() == Opcode.RET;
    }

    public static boolean mightThrowException(final CodeElement codeElement) {
        if (!(codeElement instanceof Instruction)) return false;
        Opcode opcode = ((Instruction) codeElement).opcode();
        switch (opcode) {
            // NullPointerException, ArrayIndexOutOfBoundsException
            case Opcode.BALOAD:
            case Opcode.DALOAD:
            case Opcode.FALOAD:
            case Opcode.IALOAD:
            case Opcode.LALOAD:
            case Opcode.BASTORE:
            case Opcode.CASTORE:
            case Opcode.DASTORE:
            case Opcode.FASTORE:
            case Opcode.IASTORE:
            case Opcode.LASTORE:
            case Opcode.AALOAD:
            case Opcode.CALOAD:
            case Opcode.SALOAD:
            case Opcode.SASTORE:
                // NullPointerException, ArrayIndexOutOfBoundsException,
                // ArrayStoreException
            case Opcode.AASTORE:
                // NullPointerException
            case Opcode.ARRAYLENGTH:
            case Opcode.ATHROW:
            case Opcode.GETFIELD:
            case Opcode.PUTFIELD:
                // NullPointerException, StackOverflowError
            case Opcode.INVOKEINTERFACE:
            case Opcode.INVOKESPECIAL:
            case Opcode.INVOKEVIRTUAL:
                // StackOverflowError
            case Opcode.INVOKESTATIC:
                // NegativeArraySizeException
            case Opcode.ANEWARRAY:
                // NegativeArraySizeException, OutOfMemoryError
            case Opcode.NEWARRAY:
            case Opcode.MULTIANEWARRAY:
                // OutOfMemoryError, InstantiationError
            case Opcode.NEW:
                // OutOfMemoryError
            case Opcode.LDC:
                // ClassCastException
            case Opcode.CHECKCAST:
                // ArithmeticException
            case Opcode.IDIV:
            case Opcode.IREM:
            case Opcode.LDIV:
            case Opcode.LREM:
                // New instruction in JDK7
            case Opcode.INVOKEDYNAMIC:
                return true;
            default:
                return false;
        }
    }


    public static InvokeInstruction boxValueOnStack(TypeKind typeKind) {
        switch (typeKind) {
            case TypeKind.BooleanType -> {
                return __constructValueOf(Boolean.class, boolean.class);
            }
            case TypeKind.ByteType -> {
                return __constructValueOf(Byte.class, byte.class);
            }
            case TypeKind.CharType -> {
                return __constructValueOf(Character.class, char.class);
            }
            case TypeKind.DoubleType -> {
                return __constructValueOf(Double.class, double.class);
            }
            case TypeKind.FloatType -> {
                return __constructValueOf(Float.class, float.class);
            }
            case TypeKind.IntType -> {
                return __constructValueOf(Integer.class, int.class);
            }
            case TypeKind.LongType -> {
                return __constructValueOf(Long.class, long.class);
            }
            case TypeKind.ShortType -> {
                return __constructValueOf(Short.class, short.class);
            }
            default -> {
                throw new DiSLFatalException (
                        "Impossible to box type: "+ typeKind.descriptor()
                );
            }
        }
    }


    private static InvokeInstruction __constructValueOf(
            final Class<?> boxClass, final Class<?> primitiveClass
    ) {
        String ownerDesc = boxClass.descriptorString();

        ClassDesc returnType = ClassDesc.ofDescriptor(ownerDesc);
        ClassDesc parameter = ClassDesc.ofDescriptor(primitiveClass.descriptorString());
        MethodTypeDesc typeDesc = MethodTypeDesc.of(returnType, List.of(parameter));
        String methodDesc = typeDesc.descriptorString();

        String methodName = "valueOf";

        return invokeStatic(ownerDesc, methodName, methodDesc);
    }


    public static final String formatLineNo(final String format, final CodeElement codeElement, final List<CodeElement> codeElementList) {
        final int lineNo = getLineNo(codeElement, codeElementList);
        if (lineNo > 0) {
            return String.format(format, lineNo);
        } else {
            return "";
        }
    }


    // TODO the line number should appear before the instruction as a pseudo instruction
    public static int getLineNo(final CodeElement codeElement, final List<CodeElement> codeElementList) {
        // get the previous element
        int index = codeElementList.indexOf(codeElement) -1;
        while (index >= 0) {
            if (codeElementList.get(index) instanceof LineNumber) {
                return ((LineNumber) codeElementList.get(index)).line();
            }
            index--;
        }
        return -1;
    }

    // TODO this might not be needed as it does not use ASM to replace . with /
    public static String typeName(final ClassModel classModel) {
        return JavaNames.internalToType(classModel.thisClass().name().stringValue());
    }


    // These methods are to get the runtime visible and invisible annotations of class and of methods
    // TODO should I do the same for RuntimeInvisibleTypeAnnotationsAttribute and RuntimeVisibleTypeAnnotationsAttribute???
    public static List<RuntimeVisibleAnnotationsAttribute> getVisibleAnnotation(ClassModel classModel) {
        return classModel.elementStream()
                .filter(e -> e instanceof RuntimeVisibleAnnotationsAttribute)
                .map(e -> (RuntimeVisibleAnnotationsAttribute)e)
                .toList();
    }

    public static List<RuntimeVisibleAnnotationsAttribute> getVisibleAnnotation(MethodModel methodModel) {
        return methodModel.elementStream()
                .filter(e -> e instanceof RuntimeVisibleAnnotationsAttribute)
                .map(e -> (RuntimeVisibleAnnotationsAttribute)e)
                .toList();
    }

    public static List<RuntimeInvisibleAnnotationsAttribute> getInvisibleAnnotation(ClassModel classModel) {
        return classModel.elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e)
                .toList();
    }

    public static List<RuntimeInvisibleAnnotationsAttribute> getInvisibleAnnotation(MethodModel methodModel) {
        return methodModel.elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e)
                .toList();
    }



}
