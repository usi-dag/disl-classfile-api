package ch.usi.dag.disl.processor;

import ch.usi.dag.disl.exception.DiSLFatalException;

import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;


// TODO replace all instances of ArgProcessorKind and remove it once is no longer used
public enum ArgProcessorKind {

    BOOLEAN(TypeKind.BooleanType),

    BYTE(TypeKind.ByteType) {
        @Override
        public EnumSet<ArgProcessorKind> secondaryTypes() {
            return EnumSet.of(ArgProcessorKind.BOOLEAN);
        }
    },

    CHAR(TypeKind.CharType),
    DOUBLE(TypeKind.DoubleType),
    FLOAT(TypeKind.FloatType),

    INT(TypeKind.IntType) {
        @Override
        public Set<ArgProcessorKind> secondaryTypes() {
            return EnumSet.of(BOOLEAN, BYTE, SHORT);
        }
    },

    LONG(TypeKind.LongType),

    SHORT(TypeKind.ShortType) {
        @Override
        public Set<ArgProcessorKind> secondaryTypes() {
            return EnumSet.of(BOOLEAN, BYTE);
        }
    },

    OBJECT(TypeKind.ReferenceType);

    private final TypeKind __primaryType;
    private ArgProcessorKind(final TypeKind primaryType) {
        __primaryType = primaryType;
    }
    public TypeKind primaryType() {
        return __primaryType;
    }
    public Set<ArgProcessorKind> secondaryTypes() {
        return Collections.emptySet();
    }

    // TODO
    public static ArgProcessorKind valueOf(final TypeKind type) {
        for (final ArgProcessorKind kind: values()) {
            if (kind.__primaryType.equals(type)) {
                return kind;
            }
        }
        // with this constructor only the void type is not accepted
        throw new DiSLFatalException(
                "conversion from %s not defined", type.typeName()
        );
    }

    public static ArgProcessorKind valueOf(final ClassDesc desc) {
        TypeKind type = TypeKind.fromDescriptor(desc.descriptorString());
        for (final ArgProcessorKind kind: values()) {
            if (kind.__primaryType.equals(type)) {
                return kind;
            }
        }
        // with this constructor only the void type is not accepted
        throw new DiSLFatalException(
                "conversion from %s not defined", desc.displayName()
        );
    }

    public static ArgProcessorKind forMethod(final MethodModel method) {
        List<ClassDesc> argDesc = method.methodTypeSymbol().parameterList();
        if (!argDesc.isEmpty()) {
            return valueOf(argDesc.getFirst());
        }
        return null;
    }

}
