package ch.usi.dag.disl.weaver.peClassFile;

import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.disl.util.JavaNames;

import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static java.lang.constant.ConstantDescs.CD_void;

public class InvocationInterpreter {
    final private HashSet<String> registeredMethods;

    public InvocationInterpreter() {
        registeredMethods = new HashSet<>();
    }

    public Object execute(InvokeInstruction instruction, List<? extends ConstValue> values) {
        Opcode opcode = instruction.opcode();

        if (opcode == Opcode.INVOKEINTERFACE) {
            return null;
        }

        if (!registeredMethods.contains(getMethodID(instruction))) {
            return null;
        }

        if (opcode == Opcode.INVOKESPECIAL && instruction.name().equalsString("<clinit>")) {
            if (!(values.getFirst().cst instanceof Reference)) {
                return null;
            }
            for (int i = 1; i < values.size(); i++) {
                if (ClassHelper.dereference(values.get(i)) == null) {
                    return null;
                }
            }
        } else {

            for (ConstValue value : values) {
                if (value.cst == null) {
                    return null;
                }
            }
        }

        try {
            Class<?> clazz = Class.forName(ClassFileHelper.getClassName(instruction.owner().asSymbol()));
            Class<?>[] parameters = ClassHelper.getClasses(instruction.typeSymbol());

            if (parameters == null) {
                return null;
            }

            Object args = ClassHelper.getArgs(instruction, values, parameters);

            if (instruction.name().equalsString("<init>")) {
                Reference ref = (Reference) values.getFirst().cst;
                ref.setObj(clazz.getConstructor(parameters).newInstance(args));
                return null;
            } else if (!instruction.name().equalsString("<clinit>")) {
                Object caller = ClassHelper.getCaller(instruction, values);
                ClassDesc returnType = instruction.typeSymbol().returnType();
                Class<?> retType = ClassHelper.getClassFromType(returnType);

                if (retType == null) {
                    return null;
                }

                Object retValue = clazz.getMethod(instruction.name().stringValue(), parameters)
                        .invoke(caller, args);

                return ClassHelper.address(retValue, retType);
            }
            return null;
        }  catch (Exception e) {
            return null;
        }
    }

    public void register(String owner, String name, String descriptor) {
        registeredMethods.add(JavaNames.methodUniqueName(owner, name, descriptor));
    }

    public void register(Class<?> clazz) {
        String owner = clazz.getName().replace(".", "/");

        for (Constructor<?> constructor: clazz.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            List<ClassDesc> parametersDesc = Arrays.stream(parameters)
                    .map(d -> ClassDesc.ofDescriptor(d.descriptorString())).toList();
            register(owner, "<init>", MethodTypeDesc.of(CD_void, parametersDesc).descriptorString());
        }

        for (Method method: clazz.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            List<ClassDesc> parameterDesc = Arrays.stream(parameters)
                    .map(d -> ClassDesc.ofDescriptor(d.descriptorString())).toList();
            MethodTypeDesc methodDesc = MethodTypeDesc.of(
                    ClassDesc.ofDescriptor(method.getReturnType().descriptorString()),
                    parameterDesc);
            register(owner, method.getName(), methodDesc.descriptorString());
        }
    }

    private String getMethodID(InvokeInstruction min) {
        return JavaNames.methodUniqueName(
                min.owner().name().stringValue(),
                min.name().stringValue(),
                min.typeSymbol().descriptorString());
    }

    public boolean isRegistered(InvokeInstruction instruction) {
        return registeredMethods.contains(getMethodID(instruction));
    }

    private static InvocationInterpreter instance;

    public static InvocationInterpreter getInstance() {

        if (instance == null) {

            instance = new InvocationInterpreter();

            instance.register(Boolean.class);
            instance.register(Byte.class);
            instance.register(Character.class);
            instance.register(Double.class);
            instance.register(Float.class);
            instance.register(Integer.class);
            instance.register(Long.class);
            instance.register(Short.class);
            instance.register(String.class);
            instance.register(StringBuilder.class);
        }

        return instance;
    }

}
