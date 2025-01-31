package ch.usi.dag.dislreserver.shadow;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.stream.Stream;

import org.objectweb.asm.Type;


final class ArrayShadowClass extends ShadowClass {

    private final ShadowClass __superClass;

    private final ShadowClass __componentClass;

    //

    ArrayShadowClass (
        final long netReference, final Type type,
        final ShadowObject classLoader, final ShadowClass superClass,
        final ShadowClass componentClass
    ) {
        super (netReference, type, classLoader);

        __superClass = superClass;
        __componentClass = componentClass;
    }

    //

    public int getDimensionCount () {
        return _type ().getDimensions ();
    }


    @Override
    public ShadowClass getComponentType () {
        if (__componentClass != null) {
            return __componentClass;
        }

        throw new UnsupportedOperationException ("not yet implemented");
    }


    @Override
    public String getComponentDescriptor () {
        return _type ().getElementType ().getDescriptor ();
    }

    //

    /**
     * @see Class#isInstance(Object)
     */
    @Override
    public boolean isInstance (final ShadowObject object) {
        return equals (object.getShadowClass ());
    }


    /**
     * @see Class#isAssignableFrom(Class)
     */
    @Override
    public boolean isAssignableFrom (final ShadowClass other) {
        if (this.equals (other)) {
            return true;
        }

        if (other instanceof ArrayShadowClass) {
            // This is needed until we properly implement componentType.
            if (__componentClass == null) {
                throw new UnsupportedOperationException ("component type comparison not implemented yet");
            }

            return __componentClass.isAssignableFrom (other.getComponentType ());
        }

        return false;
    }

    //

    @Override
    public int getModifiers () {
        //
        // Array classes are ABSTRACT and FINAL, but the access modifier is
        // derived from the component type. We make the array classes public
        // until we have a valid component type.
        //
        // FIXME Return access modifier based on the component type.
        //
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    //

    @Override
    public ShadowClass getSuperclass () {
        // Array types (should) have Object as the super class.
        return __superClass;
    }


    @Override
    public ShadowClass [] getInterfaces () {
        throw new UnsupportedOperationException ("not yet implemented");
    }


    @Override
    public String [] getInterfaceDescriptors () {
        // Array types implement Cloneable and Serializable interfaces.
        return new String [] {
            Type.getType (Cloneable.class).getDescriptor (),
            Type.getType (Serializable.class).getDescriptor ()
        };
    }

    //

    @Override
    protected Stream <FieldInfo> _declaredFields () {
        // Array types have no declared fields.
        return Stream.empty ();
    }


    @Override
    protected Stream <MethodInfo> _declaredMethods () {
        // Array types have no declared methods.
        return Stream.empty ();
    }

}
