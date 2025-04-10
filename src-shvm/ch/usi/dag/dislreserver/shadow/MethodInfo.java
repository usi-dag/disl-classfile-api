package ch.usi.dag.dislreserver.shadow;

import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;


public final class MethodInfo {

    private final MethodModel __methodNode;

    //

    MethodInfo (final MethodModel methodNode) {
        __methodNode = methodNode;
    }

    //

    @Override
    public int hashCode () {
        return
            (this.getName ().hashCode () & 0xFFFF0000)
            |
            (this.getDescriptor ().hashCode () & 0x0000FFFF);
    }


    @Override
    public boolean equals (final Object object) {
        if (object instanceof MethodInfo other) {
            return
                this.getName ().equals (other.getName ())
                &&
                this.getDescriptor ().equals (other.getDescriptor ());
        }

        return false;
    }

    //

    boolean matches (final String name, final String [] paramDescriptors) {
        return
            this.getName ().equals (name)
            &&
            Arrays.equals (paramDescriptors, this.getParameterDescriptors ());
    }

    //

    public String getName () {
        return __methodNode.methodName().stringValue();
    }


    public int getModifiers () {
        return __methodNode.flags().flagsMask();
    }


    public String getDescriptor () {
        return __methodNode.methodTypeSymbol().descriptorString();
    }


    public String getReturnDescriptor () {
        return __methodNode.methodTypeSymbol().returnType().descriptorString();
    }


    public String [] getParameterDescriptors () {
        return __methodNode.methodTypeSymbol().parameterList().stream().map(ClassDesc::descriptorString).toArray(String[]::new);
    }


    public String [] getExceptionDescriptors () {
        Optional<CodeModel> code = __methodNode.code();
        return code.map(codeModel -> codeModel.exceptionHandlers().stream()
                .map(e -> e.catchType().isEmpty() ? Exception.class.descriptorString() : e.catchType().get().asSymbol().descriptorString())
                .toArray(String[]::new)).orElseGet(() -> new String[0]);
    }

    //

    public boolean isPublic() {
        return Modifier.isPublic (getModifiers ());
    }


    public boolean isConstructor () {
        return "<init>".equals (__methodNode.methodName().stringValue());
    }


    public boolean isClassInitializer () {
        return "<clinit>".equals (__methodNode.methodName().stringValue());
    }

}
