package ch.usi.dag.dislreserver.shadow;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.NestMembersAttribute;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessFlag;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;


final class ObjectShadowClass extends ShadowClass {

    private final ShadowClass __superClass;

    private final ClassModel __classNode;

    //

    ObjectShadowClass (
        final long netReference, final ClassDesc type,
        final ShadowObject classLoader, final ShadowClass superClass,
        final ClassModel classNode
    ) {
        super (netReference, type, classLoader);

        __superClass = superClass;
        __classNode = classNode;
    }

    //

    /**
     * @see Class#isInstance(Object)
     */
    @Override
    public boolean isInstance (final ShadowObject object) {
        if (object == null) {
            return false;
        }

        if (object instanceof ShadowClass) {
            //
            // ShadowClass instances have no shadow class. If this shadow class
            // represents the Object or Class types, then the other shadow class
            // can be considered an instance of this type.
            //
            return __isObjectClass () || __isClassClass ();

        } else {
            //
            // For all other cases, check whether the type represented by the
            // given class is assignable to the type represented by this class.
            //
            return this.isAssignableFrom (object.getShadowClass ());
        }
    }


    private boolean __isObjectClass () {
        return this.__superClass == null;
    }



    private boolean __isClassClass () {
        return this == ShadowClassTable.JAVA_LANG_CLASS.get ();
    }


    @Override
    public boolean isAssignableFrom (final ShadowClass other) {
        //
        // A reference class type is not assignable from a primitive type.
        // This check also ensures that we throw a NullPointerException if
        // the other reference is null.
        //
        if (other.isPrimitive ()) {
            return false;
        }

        // Classes loaded by different class loaders are not assignable.
        if (this.getClassLoader () != other.getClassLoader ()) {
            return false;
        }

        // If this class represents Object, then anything is assignable to it.
        if (__isObjectClass ()) {
            return true;
        }

        // In all other cases, check along the inheritance hierarchy.
        return __isAssignableFrom (other);
    }


    private boolean __isAssignableFrom (final ShadowClass other) {
        if (other == null) {
            // We reached the root of the inheritance hierarchy.
            return false;
        }

        if (this.equals (other)) {
            // We found a matching class in the inheritance hierarchy.
            return true;
        }

        for (final ShadowClass otherIface : other.getInterfaces ()) {
            if (this.isAssignableFrom (otherIface)) {
                // The other class implements an assignable interface.
                return true;
            }
        }

        return __isAssignableFrom (other.getSuperclass ());
    }

    //

    @Override
    public List<AccessFlag> getModifiers () {
        // TODO For some reasons the flags of the class model do not contain the flag "Static" even if
        //  the actual class has the modifier. This might be a bug of the classFile api, for now I simply merge the
        //  flags of the classModel with the flags of the class object.
        Set<AccessFlag> accessFlags = new HashSet<>(__classNode.flags().flags()); // originally is a unmodifiable set
        try {
            Class<?> actualClass = __classNode.thisClass().asSymbol().resolveConstantDesc(MethodHandles.lookup());
            accessFlags.addAll(actualClass.accessFlags());
        } catch (Exception _) {}
        return accessFlags.stream().toList();
    }

    //

    /**
     * @see Class#getSuperclass()
     */
    @Override
    public ShadowClass getSuperclass () {
        return __superClass;
    }


    @Override
    public ShadowClass [] getInterfaces () {
        throw new UnsupportedOperationException ("not yet implemented");
    }


    @Override
    public String [] getInterfaceDescriptors () {
        return __classNode.interfaces().stream().map(i -> i.asSymbol().descriptorString()).toArray(String[]::new);
    }

    //

    @Override
    protected Stream <FieldInfo> _declaredFields () {
        return __classNode.fields().stream().map(FieldInfo::new);
    }


    @Override
    protected Stream <MethodInfo> _declaredMethods () {
        return __classNode.methods().stream ().map (MethodInfo::new);
    }

    //

    @Override
    public String [] getDeclaredClassDescriptors () {
        Optional<NestMembersAttribute> innerClasses = __classNode.findAttribute(Attributes.nestMembers());
        return innerClasses.map(
                nestMembersAttribute ->
                        nestMembersAttribute.nestMembers().stream()
                                .map(i -> i.asSymbol().descriptorString())
                                .toArray(String[]::new))
                .orElseGet(() -> new String[0]);
    }

}
