package ch.usi.dag.disl.util;

import ch.usi.dag.disl.CustomCodeElements.FutureLabelTarget;
import ch.usi.dag.disl.exception.DiSLFatalException;
import ch.usi.dag.disl.util.cfgCF.BasicBlockCF;
import ch.usi.dag.disl.util.cfgCF.ControlFlowGraph;

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
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.*;

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

    public static ConstantInstruction loadConst(final Object value) {
        ConstantPoolBuilder constantPoolBuilder = ConstantPoolBuilder.of();
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
                    LoadableConstantEntry constantEntry = constantPoolBuilder.loadableConstantEntry(intValue);
                    return ConstantInstruction.ofLoad(Opcode.LDC, constantEntry);
                }
            }
            // TODO what about char????? is fine if it is handled by the default LDC ???
            case Float f -> {
                if (f == 0) {
                    return ConstantInstruction.ofIntrinsic(Opcode.FCONST_0);
                } else if (f == 1) {
                    return ConstantInstruction.ofIntrinsic(Opcode.FCONST_1);
                } else if (f == 2) {
                    return ConstantInstruction.ofIntrinsic(Opcode.FCONST_2);
                }
                return ConstantInstruction.ofLoad(Opcode.LDC, constantPoolBuilder.floatEntry(f));
            }
            case Long l-> {
                if (l == 0) {
                    return ConstantInstruction.ofIntrinsic(Opcode.LCONST_0);
                } else if (l == 1) {
                    return ConstantInstruction.ofIntrinsic(Opcode.LCONST_1);
                }
                return ConstantInstruction.ofLoad(Opcode.LDC2_W, constantPoolBuilder.longEntry(l));
            }
            case Double d -> {
                if (d == 0) {
                    return ConstantInstruction.ofIntrinsic(Opcode.DCONST_0);
                } else if (d == 1) {
                    return ConstantInstruction.ofIntrinsic(Opcode.DCONST_1);
                }
                return ConstantInstruction.ofLoad(Opcode.LDC2_W, constantPoolBuilder.doubleEntry(d));
            }
            case String s -> {
                return ConstantInstruction.ofLoad(Opcode.LDC, constantPoolBuilder.stringEntry(s));
            }
            default -> {
                ClassDesc classDesc = ClassDesc.ofDescriptor(value.getClass().descriptorString());
                LoadableConstantEntry constantEntry = constantPoolBuilder.loadableConstantEntry(classDesc);
                return ConstantInstruction.ofLoad(Opcode.LDC, constantEntry);
            }
        }
    }

    public static String getStringConstOperand(final CodeElement codeElement) {
        if (codeElement instanceof ConstantInstruction.LoadConstantInstruction loadConstantInstruction) {
            LoadableConstantEntry entry = loadConstantInstruction.constantEntry();
            ConstantDesc constant = entry.constantValue();
            if (constant instanceof String string) {
                return string;
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
                codeElement instanceof FieldInstruction fieldInstruction &&
                fieldInstruction.opcode() == Opcode.GETSTATIC
        ) {

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
        return loadVar(TypeKind.REFERENCE, slot);
    }

    public static LoadInstruction loadVar(final TypeKind typeKind, final int slot) {
        return LoadInstruction.of(typeKind.asLoadable(), slot);
    }


    public static StoreInstruction storeObjectVar(final int slot) {
        return storeVar(TypeKind.REFERENCE, slot);
    }

    public static StoreInstruction storeVar(final TypeKind typeKind, final int slot) {
        return StoreInstruction.of(typeKind, slot);
    }

    public static ConstantInstruction.IntrinsicConstantInstruction loadNull() {
        return loadDefault(TypeKind.REFERENCE);
    }

    public static ConstantInstruction.IntrinsicConstantInstruction loadDefault(final TypeKind typeKind) {
        Opcode opcode;
        switch (typeKind) {
            case BOOLEAN, BYTE, CHAR, INT, SHORT -> {
                opcode = Opcode.ICONST_0;
            }
            case LONG -> opcode = Opcode.LCONST_0;
            case FLOAT -> opcode = Opcode.FCONST_0;
            case DOUBLE -> opcode = Opcode.DCONST_0;
            // this type is for both object and array
            case REFERENCE -> opcode = Opcode.ACONST_NULL;
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
        ClassDesc classDesc; // ins some case the string ownerDesc was not a valid descriptor, this try/catch also account for that
        try {
            classDesc = ClassDesc.ofDescriptor(ownerDesc);
        } catch (Exception e) {
            classDesc = ClassDesc.ofInternalName(ownerDesc);
        }
        ClassEntry classEntry = constantPoolBuilder.classEntry(classDesc);
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

    public static MethodTypeDesc getMethodDescriptor(final Method method) {
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
    public static int getParameterSlot(final MethodModelCopy methodModel, final int paramIndex) {
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

    public static int getParameterSlotCount(final MethodModelCopy methodModel) {
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

    /**
     * find the next Instruction after start
     * @param instructions list of CodeElement
     * @param start the element to start the search
     * @return Instruction or null if not found
     */
    public static Instruction nextRealInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start) + 1;
        if (index >= instructions.size()) {
            return null;
        }
        for (int i = index; i < instructions.size(); i ++) {
            if (instructions.get(i) instanceof Instruction) {
                return (Instruction) instructions.get(i);
            }
        }
        return null;
    }

    /**
     * find the next Instruction after start or start if is an Instruction
     * @param instructions list of CodeElement
     * @param start the element to start the search
     * @return Instruction or null if not found
     */
    public static Instruction firstNextRealInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start);
        if (index < 0) {
            return null;
        }
        for (int i = index; i < instructions.size(); i ++) {
            if (instructions.get(i) instanceof Instruction) {
                return (Instruction) instructions.get(i);
            }
        }
        return null;
    }


    /**
     * find the first real instruction before start
     * @param instructions list of CodeElement
     * @param start element to start the search
     * @return Instruction or null if not found
     */
    public static Instruction previousRealInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start) -1;
        if (index < 0) {
            return null;
        }
        while (index >= 0) {
            if (instructions.get(index) instanceof Instruction) {
                return (Instruction) instructions.get(index);
            }
            index -= 1;
        }
        return null;
    }

    /**
     * find the first real instruction before start or start itself if is an Instruction
     * @param instructions list of CodeElement
     * @param start element to start the search
     * @return Instruction or null if not found
     */
    public static Instruction firstPreviousRealInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start);
        if (index < 0) {
            return null;
        }
        while (index >= 0) {
            if (instructions.get(index) instanceof Instruction) {
                return (Instruction) instructions.get(index);
            }
            index -= 1;
        }
        return null;
    }

    // create a map from Label to its actual instruction LabelTarget (of FutureLabelTarget), this is for convenience since
    // in the classFile the jump instructions do not contain the LabelTarget but only the Label
    public static Map<Label, CodeElement> getLabelTargetMap(List<CodeElement> instructions) {
        try {
            return instructions.stream()
                    .filter(e -> e instanceof LabelTarget || e instanceof FutureLabelTarget)
                    .filter(e -> {
                        if (e instanceof FutureLabelTarget futureLabelTarget) {
                            return futureLabelTarget.hasLabel();
                        }
                        return true;
                    })
                    .collect(Collectors.toMap(e -> {
                        if (e instanceof LabelTarget labelTarget) {
                            return labelTarget.label();
                        } else {
                            FutureLabelTarget futureLabelTarget = (FutureLabelTarget) e;
                            return futureLabelTarget.getLabel();
                        }
                    }, v -> v));
        } catch (Exception e) {
            // TODO in case there two equal labels, then this will throw.
            //  Need to understand why this can happen, since it should not be possible to have the same labels in two different spots
            findDoubleLabel(instructions);
            throw new RuntimeException(e);
        }
    }

    public static boolean findDoubleLabel(List<CodeElement> instructions) {
        Map<Label, Integer> map = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            CodeElement element = instructions.get(i);
            Label label = null;
            if (element instanceof LabelTarget labelTarget) {
                label = labelTarget.label();
            } else if (element instanceof FutureLabelTarget f && f.hasLabel()) {
                label = f.getLabel();
            }
            if (label != null) {
                if (map.containsKey(label)) {
                    int index = map.get(label);
                    System.out.println("label already in map, elements at index: " + index + " and: " + i + " have the same label" );
                    System.out.println("index: " + index + " -> " + instructions.get(index));
                    System.out.println("index: " + i + " -> " + instructions.get(i));
                    return true;
                } else {
                    map.put(label, i);
                }
            }
        }
        return false;
    }

    public static CodeElement nextInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start);
        if (index < 0 || index + 1 >= instructions.size()) {
            return null;
        }
        return instructions.get(index+1);
    }

    public static CodeElement previousInstruction(List<CodeElement> instructions, CodeElement start) {
        if (start == null || instructions == null) {
            return null;
        }
        int index = instructions.indexOf(start);
        if (index <= 0) {
            return null;
        }
        return instructions.get(index -1);
    }

    // TODO this should be correct, but it should also give true to
    //  encapsulated primitive such as Integer
    public static boolean isReferenceType(TypeKind typeKind) {
        return typeKind.equals(TypeKind.REFERENCE);
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
        if (!(codeElement instanceof Instruction instruction)) {
            return false;
        }
        // TODO should return be included????
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
        return switch (opcode) {
            case Opcode.BALOAD, Opcode.DALOAD, Opcode.FALOAD, Opcode.IALOAD, Opcode.LALOAD, Opcode.BASTORE,
                 Opcode.CASTORE, Opcode.DASTORE, Opcode.FASTORE, Opcode.IASTORE, Opcode.LASTORE, Opcode.AALOAD,
                 Opcode.CALOAD, Opcode.SALOAD, Opcode.SASTORE, Opcode.AASTORE, Opcode.ARRAYLENGTH, Opcode.ATHROW,
                 Opcode.GETFIELD, Opcode.PUTFIELD, Opcode.INVOKEINTERFACE, Opcode.INVOKESPECIAL, Opcode.INVOKEVIRTUAL,
                 Opcode.INVOKESTATIC, Opcode.ANEWARRAY, Opcode.NEWARRAY, Opcode.MULTIANEWARRAY, Opcode.NEW, Opcode.LDC,
                 Opcode.CHECKCAST, Opcode.IDIV, Opcode.IREM, Opcode.LDIV, Opcode.LREM, Opcode.INVOKEDYNAMIC -> true;
            default -> false;
        };
    }


    public static InvokeInstruction boxValueOnStack(TypeKind typeKind) {
        switch (typeKind) {
            case TypeKind.BOOLEAN -> {
                return __constructValueOf(Boolean.class, boolean.class);
            }
            case TypeKind.BYTE -> {
                return __constructValueOf(Byte.class, byte.class);
            }
            case TypeKind.CHAR -> {
                return __constructValueOf(Character.class, char.class);
            }
            case TypeKind.DOUBLE -> {
                return __constructValueOf(Double.class, double.class);
            }
            case TypeKind.FLOAT -> {
                return __constructValueOf(Float.class, float.class);
            }
            case TypeKind.INT -> {
                return __constructValueOf(Integer.class, int.class);
            }
            case TypeKind.LONG -> {
                return __constructValueOf(Long.class, long.class);
            }
            case TypeKind.SHORT -> {
                return __constructValueOf(Short.class, short.class);
            }
            default -> {
                throw new DiSLFatalException (
                        "Impossible to box type: "+ typeKind
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
    public static List<Annotation> getVisibleAnnotation(ClassModel classModel) {
        RuntimeVisibleAnnotationsAttribute a = classModel.elementStream()
                .filter(e -> e instanceof RuntimeVisibleAnnotationsAttribute)
                .map(e -> (RuntimeVisibleAnnotationsAttribute)e)
                .findFirst().orElse(null);
        if (a == null) {
            return new ArrayList<>();
        }
        return a.annotations();
    }

    public static List<Annotation> getVisibleAnnotation(MethodModelCopy methodModel) {
        RuntimeVisibleAnnotationsAttribute a = methodModel.original.elementStream()
                .filter(e -> e instanceof RuntimeVisibleAnnotationsAttribute)
                .map(e -> (RuntimeVisibleAnnotationsAttribute)e)
                .findFirst().orElse(null);
        if (a == null) {
            return new ArrayList<>();
        }
        return a.annotations();
    }

    public static List<Annotation> getInvisibleAnnotation(ClassModel classModel) {
        RuntimeInvisibleAnnotationsAttribute a = classModel.elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e)
                .findFirst().orElse(null);
        if (a == null) {
            return new ArrayList<>();
        }
        return a.annotations();
    }

    public static List<Annotation> getInvisibleAnnotation(MethodModelCopy methodModel) {
        RuntimeInvisibleAnnotationsAttribute a = methodModel.original.elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e)
                .findFirst().orElse(null);
        if (a == null) {
            return new ArrayList<>();
        }
        return a.annotations();
    }

    public static List<Annotation> getFieldInvisibleAnnotations(FieldModel field) {
        // In a field there should only be a single element of type RuntimeInvisibleAnnotationsAttribute
        RuntimeInvisibleAnnotationsAttribute annotationsAttribute = field.elementStream()
                .filter(e -> e instanceof RuntimeInvisibleAnnotationsAttribute)
                .map(e -> (RuntimeInvisibleAnnotationsAttribute)e )
                .findFirst().orElse(null);
        if (annotationsAttribute == null) {
            return new ArrayList<>();
        }
        return annotationsAttribute.annotations();
    }


    /**
     * insert an element after a specified target element
     * @param elementTarget target to insert after, must be in the list
     * @param elementToInsert new element to insert
     * @param instructions list where to insert the element
     * @return true if successful
     */
    public static boolean insert(CodeElement elementTarget, CodeElement elementToInsert, List<CodeElement> instructions) {
        // add 1 since we want to insert it afterward
        final int index = instructions.indexOf(elementTarget) + 1;
        if (index > 0) {
            if (index >= instructions.size()) {
                // append it at the end if the index is outside the bound
                instructions.add(elementToInsert);
                return true;
            }
            instructions.add(index, elementToInsert);
            return true;
        }

        return false;
    }

    /**
     * insert an element after a specified target element
     * @param elementTarget target to insert after, must be in the list
     * @param elementsToInsert new elements to insert
     * @param instructions list where to insert the elements
     * @return true if successful
     */
    public static boolean insertAll(CodeElement elementTarget, List<? extends CodeElement> elementsToInsert, List<CodeElement> instructions) {
        // add 1 since we want to insert it afterward
        final int index = instructions.indexOf(elementTarget) + 1;
        if (index > 0) {
            if (index >= instructions.size()) {
                // append all at the end if the index is outside the bound
                return instructions.addAll(elementsToInsert);
            }
            return instructions.addAll(index, elementsToInsert);
        }

        return false;
    }

    /**
     * Insert a code element before the given target
     * @param elementTarget the element target inside the list
     * @param elementToInsert the element to insert before the target
     * @param instructions list of instructions where the element is going to be inserted
     * @return true if successful
     */
    public static boolean insertBefore(CodeElement elementTarget, CodeElement elementToInsert, List<CodeElement> instructions) {
        final int index = instructions.indexOf(elementTarget);
        if (index >= 0) {
            instructions.add(index, elementToInsert);
            return true;
        }
        return false;
    }

    /**
     * insert a list of code elements in another list before a given target
     * @param elementTarget the target to insert the element before
     * @param elementsToInsert element to insert
     * @param instructions list of element where the new instructions will be inserted
     * @return true if successful
     */
    public static boolean insertAllBefore(CodeElement elementTarget, List<? extends CodeElement> elementsToInsert, List<CodeElement> instructions) {
        final int index = instructions.indexOf(elementTarget);
        if (index >= 0) {
            return instructions.addAll(index, elementsToInsert);
        }
        return false;
    }


    // these methods are for getting max locals and max stack, they were originally
    // in MaxCalculator.java

    /**
     *
     * @param methodTypeDesc descriptor of the method
     * @return the size of arguments and return like the asm version Type.getArgumentAndReturnSize(method)
     */
    public static int getArgumentAndReturnSize(final MethodTypeDesc methodTypeDesc) {
        int argumentSize = 1;
        for (ClassDesc argument: methodTypeDesc.parameterList()) {
            argumentSize += TypeKind.from(argument).slotSize();
        }
        return argumentSize << 2 | TypeKind.from(methodTypeDesc.returnType()).slotSize();
    }

    /**
     * Get the number of max locals
     * @param codeElementList list of instruction of the method
     * @param methodDescriptor the descriptor of the method
     * @param flags the flags of the method
     * @return the max locals
     */
    public static int getMaxLocals(List<CodeElement> codeElementList, MethodTypeDesc methodDescriptor, AccessFlags flags) {
        int maxLocals = getArgumentAndReturnSize(methodDescriptor) >> 2;
        if (flags.has(AccessFlag.STATIC)) {
            maxLocals -= 1;
        }
        for (CodeElement codeElement: codeElementList) {
            switch (codeElement) {
                case LoadInstruction loadInstruction -> {
                    int local = loadInstruction.slot();
                    int size;
                    switch (loadInstruction.opcode()) {
                        case LLOAD,
                             LLOAD_0,
                             LLOAD_1,
                             LLOAD_2,
                             LLOAD_3,
                             LLOAD_W,
                             DLOAD,
                             DLOAD_0,
                             DLOAD_1,
                             DLOAD_2,
                             DLOAD_3,
                             DLOAD_W -> size = 2;
                        default -> size = 1;
                    }
                    maxLocals = Math.max(maxLocals, local + size);
                }
                case StoreInstruction storeInstruction -> {
                    int local = storeInstruction.slot();
                    int size;
                    switch (storeInstruction.opcode()) {
                        case LSTORE,
                             LSTORE_0,
                             LSTORE_1,
                             LSTORE_2,
                             LSTORE_3,
                             LSTORE_W,
                             DSTORE,
                             DSTORE_0,
                             DSTORE_1,
                             DSTORE_2,
                             DSTORE_3,
                             DSTORE_W -> size = 2;
                        default -> size = 1;
                    }
                    maxLocals = Math.max(maxLocals, local + size);
                }
                case IncrementInstruction incrementInstruction -> {
                    int local = incrementInstruction.slot();
                    maxLocals = Math.max(maxLocals, local + 1);
                }
                default -> {}
            }
        }
        return maxLocals;
    }

    public static int getMaxStack(List<CodeElement> codeElementList, List<ExceptionCatch> tryCatchBlocks) {
        if (codeElementList.isEmpty()) {
            return 0;
        }
        ControlFlowGraph cfg = ControlFlowGraph.build(codeElementList, tryCatchBlocks);
        List<BasicBlockCF> unvisited = cfg.getNodes();

        Instruction firstReal = ClassFileHelper.firstNextRealInstruction(codeElementList, codeElementList.getFirst());

        int maxStack = getMaxStack(0, cfg.getBasicBlock(firstReal), unvisited);

        Map<Label, CodeElement> labelTargetMap = ClassFileHelper.getLabelTargetMap(codeElementList);

        for (ExceptionCatch exceptionCatch: tryCatchBlocks) {
            maxStack = Math.max(
                    getMaxStack(1, cfg.getBasicBlock(labelTargetMap.get(exceptionCatch.handler())), unvisited),
                    maxStack
            );
        }

        return maxStack;
    }

    private static int getMaxStack(final int initialStackSize, BasicBlockCF bb, List<BasicBlockCF> unvisited) {
        if (!unvisited.remove(bb)) {
            return 0;
        }

//        List<String> strings = new ArrayList<>();
//        int index = 0;

        int maxStack = initialStackSize;
        int currentStackSize = initialStackSize;

        for (CodeElement element: bb) {
            currentStackSize = execute(currentStackSize, element);

//            if (element instanceof Instruction instruction) {
//                strings.add("stack: " + currentStackSize + ", index: " + index + ", " + instruction);
//            } else {
//                strings.add("stack: " + currentStackSize + ", index: " + index);
//            }
//            index++;

            maxStack = Math.max(currentStackSize, maxStack);
        }
//        WriteInfo info = WriteInfo.getInstance();
//        info.writeLine("START INSTRUCTIONS").writeLine("-------------------------");
//        info.writeLine("Initial Stack: " + initialStackSize);
//        info.writeLine("BB: " + bb).writeLine("unvisited: " + unvisited.size());
//        for (String s: strings) {
//            info.writeLine(s);
//        }

        for (BasicBlockCF next: bb.getSuccessors()) {
            maxStack = Math.max(getMaxStack(currentStackSize, next, unvisited), maxStack);
        }

        return maxStack;
    }

    // TODO this file is getting quite long, if there is time refactor this helper by splitting it into different files
    /**
     * The stack size variation corresponding to each JVM instruction.
     */
    public static final int[] SIZE = {
    // stack change,    ins,            stack operation
            0,      // nop              [No change]
            1,      // aconst_null      → null
            1,      // iconst_m1        → -1
            1,      // iconst_0         → 0
            1,      // iconst_1         → 1
            1,      // iconst_2         → 2
            1,      // iconst_3         → 3
            1,      // iconst_4         → 4
            1,      // iconst_5         → 5
            2,      // lconst_0         → 0L
            2,      // lconst_1         → 1L
            1,      // fconst_0         → 0.0f
            1,      // fconst_1         → 1.0f
            1,      // fconst_2         → 2.0f
            2,      // dconst_0         → 0.0
            2,      // dconst_1         → 1.0
            1,      // bipush           → value
            1,      // sipush           → value
            1,      // ldc              → value
            1,      // ldc_w            → value
            2,      // ldc2_w           → value
            1,      // iload            → value
            2,      // lload            → value
            1,      // fload            → value
            2,      // dload            → value
            1,      // aload            → objectref
            1,      // iload_0          → value
            1,      // iload_1          → value
            1,      // iload_2          → value
            1,      // iload_3          → value
            2,      // lload_0          → value
            2,      // lload_1          → value
            2,      // lload_2          → value
            2,      // lload_3          → value
            1,      // fload_0          → value
            1,      // fload_1          → value
            1,      // fload_2          → value
            1,      // fload_3          → value
            2,      // dload_0          → value
            2,      // dload_1          → value
            2,      // dload_2          → value
            2,      // dload_3          → value
            1,      // aload_0          → objectref
            1,      // aload_1          → objectref
            1,      // aload_2          → objectref
            1,      // aload_3          → objectref
            -1,     // iaload           arrayref, index → value
            0,      // laload           arrayref, index → value
            -1,     // faload           arrayref, index → value
            0,      // daload           arrayref, index → value
            -1,     // aaload           arrayref, index → objectref
            -1,     // baload           arrayref, index → value
            -1,     // caload           arrayref, index → value
            -1,     // saload           arrayref, index → value
            -1,     // istore           value →
            -2,     // lstore           value →
            -1,     // fstore           value →
            -2,     // dstore           value →
            -1,     // astore           objectref →
            -1,     // istore_0         value →
            -1,     // istore_1         value →
            -1,     // istore_2         value →
            -1,     // istore_3         value →
            -2,     // lstore_0         value →
            -2,     // lstore_1         value →
            -2,     // lstore_2         value →
            -2,     // lstore_3         value →
            -1,     // fstore_0         value →
            -1,     // fstore_1         value →
            -1,     // fstore_2         value →
            -1,     // fstore_3         value →
            -2,     // dstore_0         value →
            -2,     // dstore_1         value →
            -2,     // dstore_2         value →
            -2,     // dstore_3         value →
            -1,     // astore_0         objectref →
            -1,     // astore_1         objectref →
            -1,     // astore_2         objectref →
            -1,     // astore_3         objectref →
            -3,     // iastore          arrayref, index, value →
            -4,     // lastore          arrayref, index, value →
            -3,     // fastore          arrayref, index, value →
            -4,     // dastore          arrayref, index, value →
            -3,     // aastore          arrayref, index, objectref →
            -3,     // bastore          arrayref, index, value →
            -3,     // castore          arrayref, index, value →
            -3,     // sastore          arrayref, index, value →
            -1,     // pop              value →
            -2,     // pop2             {value2, value1} →
            1,      // dup              value → value, value
            1,      // dup_x1           value2, value1 → value1, value2, value1
            1,      // dup_x2           value3, value2, value1 → value1, value3, value2, value1
            2,      // dup2             {value2, value1} → {value2, value1}, {value2, value1}
            2,      // dup2_x1          value3, {value2, value1} → {value2, value1}, value3, {value2, value1}
            2,      // dup2_x2          {value4, value3}, {value2, value1} → {value2, value1}, {value4, value3}, {value2, value1}
            0,      // swap             value2, value1 → value1, value2
            -1,     // iadd             value1, value2 → result
            -2,     // ladd             value1, value2 → result
            -1,     // fadd             value1, value2 → result
            -2,     // dadd             value1, value2 → result
            -1,     // isub             value1, value2 → result
            -2,     // lsub             value1, value2 → result
            -1,     // fsub             value1, value2 → result
            -2,     // dsub             value1, value2 → result
            -1,     // imul             value1, value2 → result
            -2,     // lmul             value1, value2 → result
            -1,     // fmul             value1, value2 → result
            -2,     // dmul             value1, value2 → result
            -1,     // idiv             value1, value2 → result
            -2,     // ldiv             value1, value2 → result
            -1,     // fdiv             value1, value2 → result
            -2,     // ddiv             value1, value2 → result
            // TODO is _rem correct >>>>
            -1,     // irem             value1, value2 → result
            -2,     // lrem             value1, value2 → result
            -1,     // frem             value1, value2 → result
            -2,     // drem             value1, value2 → result
            0,      // ineg             value → result
            0,      // lneg             value → result
            0,      // fneg             value → result
            0,      // dneg             value → result
            -1,     // ishl             value1, value2 → result
            -1,     // lshl             value1, value2 → result
            -1,     // ishr             value1, value2 → result
            -1,     // lshr             value1, value2 → result
            -1,     // iushr            value1, value2 → result
            -1,     // lushr            value1, value2 → result
            -1,     // iand             value1, value2 → result
            -2,     // land             value1, value2 → result
            -1,     // ior              value1, value2 → result
            -2,     // lor              value1, value2 → result
            -1,     // ixor             value1, value2 → result
            -2,     // lxor             value1, value2 → result
            0,      // iinc             [No change]
            1,      // i2l              value → result
            0,      // i2f              value → result
            1,      // i2d              value → result
            -1,     // l2i              value → result
            -1,     // l2f              value → result
            0,      // l2d              value → result
            0,      // f2i              value → result
            1,      // f2l              value → result
            1,      // f2d              value → result
            -1,     // d2i              value → result
            0,      // d2l              value → result
            -1,     // d2f              value → result
            0,      // i2b              value → result
            0,      // i2c              value → result
            0,      // i2s              value → result
            -3,     // lcmp             value1, value2 → result
            -1,     // fcmpl            value1, value2 → result
            -1,     // fcmpg            value1, value2 → result
            -3,     // dcmpl            value1, value2 → result
            -3,     // dcmpg            value1, value2 → result
            -1,     // ifeq             value →
            -1,     // ifne             value →
            -1,     // iflt             value →
            -1,     // ifge             value →
            -1,     // ifgt             value →
            -1,     // ifle             value →
            -2,     // if_icmpeq        value1, value2 →
            -2,     // if_icmpne        value1, value2 →
            -2,     // if_icmplt        value1, value2 →
            -2,     // if_icmpge        value1, value2 →
            -2,     // if_icmpgt        value1, value2 →
            -2,     // if_icmple        value1, value2 →
            -2,     // if_acmpeq        value1, value2 →
            -2,     // if_acmpne        value1, value2 →
            0,      // goto             [no change]
            1,      // jsr†             → address
            0,      // ret†             [No change]
            // TODO are index and key one slot??
            -1,     // tableswitch      index →
            -1,     // lookupswitch     key →
            -1,     // ireturn          value → [empty]
            -2,     // lreturn          value → [empty]
            -1,     // freturn          value → [empty]
            -2,     // dreturn          value → [empty]
            -1,     // areturn          objectref → [empty]
            0,      // return           → [empty]
            // the following instructions that have -999 is because the value can be 1 or 2
            // so they should be handled in the function execute(int, CodeElement)
            -999,   // getstatic        → value
            -999,   // putstatic        value →
            -999,   // getfield         objectref → value
            -999,   // putfield         objectref, value →
            -999,   // invokevirtual    objectref, [arg1, arg2, ...] → result
            -999,   // invokespecial    objectref, [arg1, arg2, ...] → result
            -999,   // invokestatic     [arg1, arg2, ...] → result
            -999,   // invokeinterface  objectref, [arg1, arg2, ...] → result
            -999,   // invokedynamic    [arg1, arg2, ...] → result
            1,      // new              → objectref
            0,      // newarray         count → arrayref
            0,      // anewarray        count → arrayref
            0,      // arraylength      arrayref → length
            -999,   // athrow           objectref → [empty], objectref
            0,      // checkcast        objectref → objectref
            0,      // instanceof       objectref → result
            -1,      // monitorenter     objectref →
            -1,      // monitorexit      objectref →
            -999,   // wide             [same as for corresponding instructions]
            -999,   // multianewarray   count1, [count2,...] → arrayref
            -1,     // ifnull           value →
            -1,     // ifnonnull        value →
            0,      // goto_w           [no change]
            1,      // jsr_w†           → address
    };


    private static int execute(int currentStackSize, CodeElement codeElement) {
        switch (codeElement) {
            case FieldInstruction fieldInstruction -> {
                int valueSize = TypeKind.from(fieldInstruction.typeSymbol()).slotSize();
                switch (fieldInstruction.opcode()) {
                    case GETSTATIC -> {
                        // → value
                        return currentStackSize + valueSize;
                    }
                    case PUTSTATIC -> {
                        // value →
                        return currentStackSize - valueSize;
                    }
                    case GETFIELD -> {
                        // objectref → value
                        return currentStackSize - 1 + valueSize;
                    }
                    case PUTFIELD -> {
                        // objectref, value →
                        return currentStackSize - 1 - valueSize;
                    }
                    default -> throw new RuntimeException("FieldInstruction cannot have opcode: " + fieldInstruction.opcode());
                }
            }
            case NewMultiArrayInstruction newMultiArrayInstruction -> {
                // subtract the number of dimensions and add one which is the ref of the created array
                // count1, [count2,...] → arrayref
                return currentStackSize + 1 - newMultiArrayInstruction.dimensions();
            }
            case InvokeInstruction invokeInstruction -> {
                int argSize = getArgumentAndReturnSize(invokeInstruction.typeSymbol());
                if (invokeInstruction.opcode() == Opcode.INVOKESTATIC) {
                    // [arg1, arg2, ...] → result
                    return currentStackSize - (argSize >> 2) + (argSize & 0x03) + 1;
                } else {
                    // for invokeVirtual, invokespecial, invokeinterface
                    // objectref, [arg1, arg2, ...] → result
                    return currentStackSize - (argSize >> 2) + (argSize & 0x03);
                }
            }
            case InvokeDynamicInstruction invokeDynamicInstruction -> {
                // [arg1, arg2, ...] → result
                int argSize = getArgumentAndReturnSize(invokeDynamicInstruction.typeSymbol());
                return currentStackSize - (argSize >> 2) + (argSize & 0x03) + 1;
            }
            case ThrowInstruction _ -> {
                // this is because the stack is cleared and a reference is pushed
                return 1;
            }
            case Instruction instruction -> {
                // handle the wide, since the stack operation is equal at the one without wide
                // we just call the original
                switch (instruction.opcode()) {
                    // TODO merge together these
                    case ALOAD_W, FLOAD_W, ILOAD_W, JSR_W -> {
                        // → objectref
                        // → value
                        // → address
                        return currentStackSize + 1;
                    }
                    case ASTORE_W, FSTORE_W, ISTORE_W -> {
                        // objectref →
                        // value →
                        return currentStackSize - 1;
                    }
                    case DLOAD_W, LLOAD_W -> {
                        // → value
                        return currentStackSize + 2;
                    }
                    case DSTORE_W, LSTORE_W -> {
                        // value →
                        return currentStackSize - 2;
                    }
                    case GOTO_W, IINC_W, RET_W -> {
                        // [No change]
                        return currentStackSize;
                    }
                    default -> {
                        return currentStackSize + SIZE[instruction.opcode().bytecode()];
                    }
                }
            }
            default -> {
                return currentStackSize;
            }
        }
    }


    public static String nameAndDescriptor(MethodModel methodModel) {
        return methodModel.methodName().stringValue() + methodModel.methodTypeSymbol().descriptorString();
    }


    /**
     * return the internal name like it would on calling getInternalName() on an ASM Type
     * @param classDesc the class descriptor
     * @return the internal name
     */
    public static String getInternalName(ClassDesc classDesc) {
        if (classDesc.isPrimitive() || classDesc.isArray()) {
            return classDesc.descriptorString();
        }
        return (classDesc.packageName() + "/" + classDesc.displayName()).replace(".", "/");
    }

    /**
     * return the className as would the call to ASM getClassName on Type
     * @param classDesc the class descriptor
     * @return the class name
     */
    public static String getClassName(ClassDesc classDesc) {
        if (classDesc.isPrimitive()) {
            return classDesc.displayName();
        } else if (classDesc.isArray()) {
            final String descriptor = classDesc.descriptorString();
            final String cleaned = descriptor.replaceFirst("^\\[+([A-Z]{1})", "").replace(";", "");
            String [] split = cleaned.split("/");
            String partial = String.join(".", Arrays.copyOf(split, split.length -1));
            if (partial.isEmpty()) {
                return classDesc.displayName();
            }
            return String.join(".", Arrays.copyOf(split, split.length -1)) + "." + classDesc.displayName();
        }
        return classDesc.packageName() + "." + classDesc.displayName();
    }

    /**
     * return the descriptor of the array component like it would on ASM getElementType() on Type
     * @param classDesc the classDesc
     * @return the component descriptor
     */
    public static String getElementType(ClassDesc classDesc) {
        return classDesc.descriptorString().replaceFirst("^\\[+", "");
    }

    /**
     * return the number of dimension of the array like it would return from ASM Type.getDimensions()
     * @param classDesc the classDesc representing the array
     * @return the dimensions
     */
    public static long getDimensions(ClassDesc classDesc) {
        return classDesc.descriptorString().chars().filter(c -> c == '[').count();
    }

    public static ClassDesc resolveConstantDesc(ConstantDesc constantDesc) throws ReflectiveOperationException {
        try {
            return ClassDesc.ofDescriptor(constantDesc.resolveConstantDesc(MethodHandles.lookup()).getClass().descriptorString());
        } catch (Exception e) {
            switch (constantDesc) {
                case ClassDesc classDesc -> {
                    return classDesc;
                }
                // TODO should I return the ClassDesc of the primitive or the Wrapper ????
                case Double _ -> {
                    return CD_double;
                }
                case Float _ -> {
                    return CD_float;
                }
                case Integer _ -> {
                    return CD_int;
                }
                case Long _ -> {
                    return CD_long;
                }
                case String _ -> {
                    return CD_String;
                }
                default -> throw new RuntimeException(e); // in case it doesn't work I throw the original exception
            }
        }
    }


    /**
     * If the ConstantDesc represent a boxed primitive type then return the unboxed TypeKind
     * otherwise return null. Example: if the desc is an Integer then return INT
     * @param desc the constant descriptor
     * @return the unboxed TypeKind or null
     */
    public static TypeKind unboxToPrimitive(ConstantDesc desc) {
        switch (desc) {
            case Float _ -> {
                return TypeKind.FLOAT;
            }
            case Integer _ -> {
                return TypeKind.INT;
            }
            case Double _ -> {
                return TypeKind.DOUBLE;
            }
            case Long _ -> {
                return TypeKind.LONG;
            }
            case ClassDesc classDesc -> {
                return unboxToPrimitive(classDesc);
            }
            default -> {
                return null;
            }
        }

    }

    private static TypeKind unboxToPrimitive(ClassDesc desc) {
        if (desc.isPrimitive()) {
            return TypeKind.from(desc);
        }
        if (CD_Integer.equals(desc)) {
            return TypeKind.INT;
        } else if (CD_Float.equals(desc)) {
            return TypeKind.FLOAT;
        } else if (CD_Long.equals(desc)) {
            return TypeKind.LONG;
        } else if (CD_Double.equals(desc)) {
            return TypeKind.DOUBLE;
        } else if (CD_Boolean.equals(desc)) {
            return TypeKind.BOOLEAN;
        } else if (CD_Byte.equals(desc)) {
            return TypeKind.BYTE;
        } else if (CD_Short.equals(desc)) {
            return TypeKind.SHORT;
        } else if (CD_Character.equals(desc)) {
            return TypeKind.CHAR;
        }
        return null;
    }

    /**
     * replace all branch instructions labels with new labels and replace the labelTarget with futureLabelTarget with the new label
     * this is used on snippets so the if the snippet is added more that once in the same code there won't be any
     * confusion when the labels are being bound
     * @param originalList the instruction to modify
     * @param codeBuilder a codeBuilder to create new labels
     * @return the modified list of instructions
     */
    public static List<CodeElement> replaceBranchAndLabelsTarget(List<CodeElement> originalList, CodeBuilder codeBuilder){
        List<CodeElement> toModify = new ArrayList<>(originalList);

        // TODO: is probably possible to optimise by using only a single map from Original Label to the new FutureLabelTarget (todo after everything work)
        Map<Label, CodeElement> labelToOriginalTarget = getLabelTargetMap(toModify);
        Map<CodeElement, FutureLabelTarget> originalTargetToFutureTarget = new HashMap<>();

        // step 1: replace all LabelTarget (and FutureLabelTarget) with new FutureLabelTarget, also keep track of them with the map "originalTargetToFutureTarget"
        for (int index = 0; index < toModify.size(); index++) {
            CodeElement current = toModify.get(index);
            if (current instanceof LabelTarget || current instanceof FutureLabelTarget) {
                FutureLabelTarget newFutureLabelTarget = new FutureLabelTarget(codeBuilder.newLabel());
                toModify.set(index, newFutureLabelTarget);
                originalTargetToFutureTarget.put(current, newFutureLabelTarget);
            }
        }

        // step 2: replace all the BranchInstruction, Lookupswitch/Tableswitch, exceptionCatch and CharacterRange with a second pass
        for (int index = 0; index < toModify.size(); index++) {
            CodeElement current = toModify.get(index);
            switch (current) {
                case BranchInstruction branchInstruction -> {
                    Label original = branchInstruction.target();
                    CodeElement originalLabelTarget = labelToOriginalTarget.get(original);
                    FutureLabelTarget newFutureLabelTarget = originalTargetToFutureTarget.get(originalLabelTarget);
                    Label newLabel = newFutureLabelTarget.getLabel();
                    BranchInstruction newBranchInstruction = getNewBranch(branchInstruction, newLabel);
                    toModify.set(index, newBranchInstruction);
                }
                case TableSwitchInstruction tableSwitchInstruction -> {
                    TableSwitchInstruction newTableSwitch = getNewTableSwitch(tableSwitchInstruction, labelToOriginalTarget, originalTargetToFutureTarget);
                    toModify.set(index, newTableSwitch);
                }
                case LookupSwitchInstruction lookupSwitchInstruction -> {
                    LookupSwitchInstruction newLookUpSwitch = getNewLookUpSwitch(lookupSwitchInstruction, labelToOriginalTarget, originalTargetToFutureTarget);
                    toModify.set(index, newLookUpSwitch);
                }
                case ExceptionCatch exceptionCatch -> {
                    ExceptionCatch newExceptionCatch = getNewExceptionCatch(exceptionCatch, labelToOriginalTarget, originalTargetToFutureTarget);
                    toModify.set(index, newExceptionCatch);
                }
                case CharacterRange characterRange -> {
                    CharacterRange newCharacterRange = getNewCharacterRange(characterRange, labelToOriginalTarget, originalTargetToFutureTarget);
                    toModify.set(index, newCharacterRange);
                }
                default -> {
                    // do nothing
                }
            }
        }

        return toModify;
    }

    // helper functions for "replaceBranchAndLabelsTarget"

    private static BranchInstruction getNewBranch(BranchInstruction old, Label newLabel) {
        return BranchInstruction.of(old.opcode(), newLabel);
    }

    // TODO TableSwitchInstruction and LookupSwitchInstruction are similar so maybe there is a way to combine the two in a single helper function (todo after everything work)
    private static TableSwitchInstruction getNewTableSwitch(
            TableSwitchInstruction old,
            Map<Label, CodeElement> labelToOriginalTarget,
            Map<CodeElement, FutureLabelTarget> originalTargetToFutureTarget
    ){
        Label oldDefaultTarget = old.defaultTarget();
        Label newDefaultTarget = originalTargetToFutureTarget.get(labelToOriginalTarget.get(oldDefaultTarget)).getLabel();

        List<SwitchCase> oldSwitchCaseList = old.cases();
        List<SwitchCase> newSwitchCaseList = new ArrayList<>();

        for (SwitchCase originalSwitchCase: oldSwitchCaseList) {
            Label originalLabel = originalSwitchCase.target();
            Label newLabel = originalTargetToFutureTarget.get(labelToOriginalTarget.get(originalLabel)).getLabel();
            SwitchCase newSwitchCase = SwitchCase.of(originalSwitchCase.caseValue(), newLabel);
            newSwitchCaseList.add(newSwitchCase);
        }

        return TableSwitchInstruction.of(old.lowValue(), old.highValue(), newDefaultTarget, newSwitchCaseList);
    }

    private static LookupSwitchInstruction getNewLookUpSwitch(
            LookupSwitchInstruction old,
            Map<Label, CodeElement> labelToOriginalTarget,
            Map<CodeElement, FutureLabelTarget> originalTargetToFutureTarget
    ) {
        Label oldDefaultTarget = old.defaultTarget();
        Label newDefaultTarget = originalTargetToFutureTarget.get(labelToOriginalTarget.get(oldDefaultTarget)).getLabel();

        List<SwitchCase> oldSwitchCaseList = old.cases();
        List<SwitchCase> newSwitchCaseList = new ArrayList<>();

        for (SwitchCase originalSwitchCase: oldSwitchCaseList) {
            Label originalLabel = originalSwitchCase.target();
            Label newLabel = originalTargetToFutureTarget.get(labelToOriginalTarget.get(originalLabel)).getLabel();
            SwitchCase newSwitchCase = SwitchCase.of(originalSwitchCase.caseValue(), newLabel);
            newSwitchCaseList.add(newSwitchCase);
        }

        return LookupSwitchInstruction.of(newDefaultTarget, newSwitchCaseList);
    }

    private static ExceptionCatch getNewExceptionCatch(ExceptionCatch old, Map<Label, CodeElement> labelToOriginalTarget,
                                                       Map<CodeElement, FutureLabelTarget> originalTargetToFutureTarget) {
        Label newHandler = originalTargetToFutureTarget.get(labelToOriginalTarget.get(old.handler())).getLabel();
        Label newTryStart = originalTargetToFutureTarget.get(labelToOriginalTarget.get(old.tryStart())).getLabel();
        Label newTryEnd = originalTargetToFutureTarget.get(labelToOriginalTarget.get(old.tryEnd())).getLabel();
        return ExceptionCatch.of(newHandler, newTryStart, newTryEnd, old.catchType());
    }

    private static CharacterRange getNewCharacterRange(CharacterRange old, Map<Label, CodeElement> labelToOriginalTarget,
                                                       Map<CodeElement, FutureLabelTarget> originalTargetToFutureTarget) {
        Label newStart = originalTargetToFutureTarget.get(labelToOriginalTarget.get(old.startScope())).getLabel();
        Label newEnd = originalTargetToFutureTarget.get(labelToOriginalTarget.get(old.endScope())).getLabel();
        return CharacterRange.of(newStart, newEnd, old.characterRangeStart(), old.characterRangeEnd(), old.flags());
    }

}
