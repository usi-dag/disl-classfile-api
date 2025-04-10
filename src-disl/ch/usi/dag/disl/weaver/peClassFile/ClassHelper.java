package ch.usi.dag.disl.weaver.peClassFile;

import ch.usi.dag.disl.util.ClassFileHelper;


import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.HashSet;
import java.util.List;

public class ClassHelper {

    public static final HashSet<String> VALUE_TYPES = new HashSet<>();

    static {
        VALUE_TYPES.add(Boolean.class.descriptorString());
        VALUE_TYPES.add(Byte.class.descriptorString());
        VALUE_TYPES.add(Character.class.descriptorString());
        VALUE_TYPES.add(Double.class.descriptorString());
        VALUE_TYPES.add(Float.class.descriptorString());
        VALUE_TYPES.add(Integer.class.descriptorString());
        VALUE_TYPES.add(Long.class.descriptorString());
        VALUE_TYPES.add(Short.class.descriptorString());
        VALUE_TYPES.add(String.class.descriptorString());
    }

    public static Class<?> getClassFromType(ClassDesc desc) {

        TypeKind type = TypeKind.from(desc);
        switch (type) {

            case BOOLEAN:
                return boolean.class;
            case BYTE:
                return byte.class;
            case CHAR:
                return char.class;
            case DOUBLE:
                return double.class;
            case FLOAT:
                return float.class;
            case INT:
                return int.class;
            case LONG:
                return long.class;
            case SHORT:
                return short.class;
            case REFERENCE:
                try {
                    return Class.forName(ClassFileHelper.getClassName(desc));
                } catch (ClassNotFoundException e) {
                    return null;
                }
                // TODO in the original it doesn't handle array
            default:
                return null;
        }
    }

    public static boolean isValueType(ClassDesc desc) {

        TypeKind typeKind = TypeKind.from(desc);
        return switch (typeKind) {
            case BOOLEAN, BYTE, CHAR, DOUBLE, FLOAT, INT, LONG, SHORT -> true;
            case REFERENCE ->
                    VALUE_TYPES.contains(desc.descriptorString());
            default -> false;
        };
    }

    public static Class<?>[] getClasses(MethodTypeDesc desc)
            throws ClassNotFoundException {

        ClassDesc[] types = desc.parameterArray();
        Class<?>[] classes = new Class<?>[types.length];

        for (int i = 0; i < types.length; i++) {

            classes[i] = getClassFromType(types[i]);

            if (classes[i] == null) {
                return null;
            }
        }

        return classes;
    }

    public static Object i2wrapper(Integer obj, Class<?> clazz) {

        int i = obj;

        if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            return i == 1;
        }

        if (clazz.equals(Byte.class) || clazz.equals(byte.class)) {
            return (byte) i;
        }

        if (clazz.equals(Character.class) || clazz.equals(char.class)) {
            return (char) i;
        }

        if (clazz.equals(Short.class) || clazz.equals(short.class)) {
            return (short) i;
        }

        return obj;
    }

    public static Object wrapper2i(Object obj, Class<?> clazz) {

        if (clazz.equals(Boolean.class) || clazz.equals(boolean.class)) {
            return ((Boolean) obj) ? 1 : 0;
        }

        if (clazz.equals(Byte.class) || clazz.equals(byte.class)) {
            return (int) (byte) (Byte) obj;
        }

        if (clazz.equals(Character.class) || clazz.equals(char.class)) {
            return (int) (char) (Character) obj;
        }

        if (clazz.equals(Short.class) || clazz.equals(short.class)) {
            return (int) (short) (Short) obj;
        }

        return obj;
    }

    public static Object dereference(Object obj) {

        if (obj instanceof Reference) {
            return ((Reference) obj).getObj();
        } else {
            return obj;
        }
    }

    public static Object dereference(Object obj, Class<?> type) {

        if (obj instanceof Integer) {
            return ClassHelper.i2wrapper((Integer) obj, type);
        } else {
            return dereference(obj);
        }
    }

    public static Object address(Object obj, Class<?> clazz) {

        if (isValueType(ClassDesc.ofDescriptor(clazz.descriptorString()))) {
            return wrapper2i(obj, clazz);
        } else {
            return new Reference(obj);
        }
    }

    public static Object getCaller(Instruction instr,
                                   List<? extends ConstValue> values) {

        if (instr.opcode() == Opcode.INVOKESTATIC) {
            return null;
        } else {
            return dereference(values.getFirst().cst);
        }
    }

    public static Object[] getArgs(Instruction instr,
                                   List<? extends ConstValue> values, Class<?>[] parameters) {

        if (instr.opcode() == Opcode.INVOKESTATIC) {

            Object[] args = new Object[values.size()];

            for (int i = 0; i < args.length; i++) {
                args[i] = dereference(values.get(i).cst, parameters[i]);
            }

            return args;
        } else {

            Object[] args = new Object[values.size() - 1];

            for (int i = 0; i < args.length; i++) {
                args[i] = dereference(values.get(i + 1).cst, parameters[i]);
            }

            return args;
        }
    }
}
