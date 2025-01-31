package ch.usi.dag.dislreserver.shadow;

import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;


final class ObjectShadowClass extends ShadowClass {

    private final ShadowClass __superClass;

    private final ClassNode __classNode;

    //

    ObjectShadowClass (
        final long netReference, final Type type,
        final ShadowObject classLoader, final ShadowClass superClass,
        final ClassNode classNode
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
    public int getModifiers () {
        // Strip modifiers that are not valid for a class.
        return __classNode.access & Modifier.classModifiers ();
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
        return __typeDescriptors (__classNode.interfaces.stream ());
    }

    //

    @Override
    protected Stream <FieldInfo> _declaredFields () {
        return __classNode.fields.stream ().map (FieldInfo::new);
    }


    @Override
    protected Stream <MethodInfo> _declaredMethods () {
        return __classNode.methods.stream ().map (MethodInfo::new);
    }

    //

    @Override
    public String [] getDeclaredClassDescriptors () {
        return __typeDescriptors (
            __classNode.innerClasses.stream ().map (icn -> icn.name)
        );
    }

    //

    private static String [] __typeDescriptors (final Stream <String> names) {
        return names.map (n -> Type.getObjectType (n).getDescriptor ()).toArray (String []::new);
    }

}
