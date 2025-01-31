package ch.usi.dag.dislreserver.shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


public abstract class ShadowClass extends ShadowObject {

    /**
     * The type (class) represented by this shadow class.
     */
    private final Type __type;

    /**
     * The class loader that loaded the class represented by this shadow class.
     * Will be {@code null} for primitive types (including {@code void}).
     */
    private final ShadowObject __classLoader;

    //

    protected ShadowClass (
        final long netReference, final Type type,
        final ShadowObject classLoader
    ) {
        super (netReference, null /* indicates Class instance */);

        __type = type;
        __classLoader = classLoader;
    }

    //

    protected final Type _type () {
        return __type;
    }

    //

    @Override
    public boolean equals (final Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof ShadowClass) {
            return __equals ((ShadowClass) object);
        }

        return false;
    }


    private boolean __equals (final ShadowClass other) {
        //
        // Two shadow classes are considered equal if they represent the
        // same type and have been loaded by the same class loader.
        //
        if (this.__type.equals (other.__type)) {
            if (this.__classLoader != null) {
                return this.__classLoader.equals (other.__classLoader);
            } else {
                return this.__classLoader == other.__classLoader;
            }
        }

        return false;
    }

	//

    @Override
    public int hashCode () {
        //
        // Use the 22 bits of the class identifier padded with 10 bits of the
        // class loader object identifier.
        //
        return (getClassId () << 22) ^ (int) (__classLoaderId () & ((1 << 10) - 1));
    }


    private final long __classLoaderId () {
        return (__classLoader != null) ? __classLoader.getId () : 0;
    }

    //

    ShadowObject getClassLoader () {
        //
        // Should return null for primitive types or for classes
        // loaded by the bootstrap class loader.
        //
        // TODO LB: Consider exposing a reference for the bootstrap class loader.
        //
        // This would diverge from the behavior of Java reflection API, but it
        // would potentially allow an analysis to attach state to a shadow
        // object representing the bootstrap class loader. Not sure if it is
        // necessary though.
        //
        return __classLoader;
    }

	//

    public String getName () {
        //
        // Avoid Type.getClassName() because it adds "class" prefix to array types.
        //
        return _javaName (__type.getInternalName ());
    }


    protected static String _javaName (final String name) {
        return name.replace ('/', '.');
    }


    public String getSimpleName () {
        return _simpleName (__canonicalName ());
    }


    protected static String _simpleName (final String name) {
        // If '.' is not found, index is -1 => +1 adjustment gives index 0
        return name.substring (name.lastIndexOf ('.') + 1);
    }


    public String getCanonicalName () {
        return __canonicalName ();
    }


    private String __canonicalName () {
        return __type.getClassName ().replace ('$',  '.');
    }


    /**
     * @see Class#getPackage()
     */
    public String getPackage () {
        final String name = getCanonicalName ();
        final int lastIndex = name.lastIndexOf ('.');

        // Return null for array/primitive classes or default package.
        return (lastIndex >= 0) ? name.substring (0, lastIndex) : null;
    }

	//

    /**
     * @see Class#isPrimitive()
     */
    public boolean isPrimitive () {
        // We rely on the ordering of sorts in ASM Type.
        return __type.getSort () < Type.ARRAY;
    }

    public boolean isArray () {
        return __type.getSort () == Type.ARRAY;
    };

    //

    public ShadowClass getComponentType () {
        // By default, a class has no component type.
        return null;
    }


    public String getComponentDescriptor () {
        // By default, a class has no component type descriptor.
        return null;
    }

	//

    public abstract boolean isInstance (ShadowObject object);


    /**
     * @throws NullPointerException
     *         if the specified {@link ShadowClass} parameter is {@code null}
     *
     * @see Class#isAssignableFrom(Class)
     */
    public abstract boolean isAssignableFrom (ShadowClass other);


    //

    /**
     * Returns {@code true} if this class matches the given {@link Type type}.
     */
    final boolean equalsType (final Type type) {
        return __type.equals (type);
    }


    /**
     * Returns {@code true} if this class is derived from the given {@link Type
     * type}. This method checks for presence of {@link Type type} in the
     * inheritance hierarchy of this class.
     */
    final boolean extendsType (final Type type) {
        if (equalsType (type)) {
            return true;

        } else {
            final ShadowClass superClass = getSuperclass ();
            return (superClass != null) ? superClass.extendsType (type) : false;
        }
    }

	//

    public abstract int getModifiers ();


    public boolean isInterface () {
        return __hasModifier (Opcodes.ACC_INTERFACE);
    }


    public boolean isAnnotation () {
        return __hasModifier (Opcodes.ACC_ANNOTATION);
    }


    public boolean isSynthetic () {
        return __hasModifier (Opcodes.ACC_SYNTHETIC);
    }


    public boolean isEnum () {
        return __hasModifier (Opcodes.ACC_ENUM);
    }


    private boolean __hasModifier (final int flag) {
        return (getModifiers () & flag) != 0;
    }

	//

    public abstract ShadowClass getSuperclass ();

    public abstract ShadowClass [] getInterfaces ();

    public abstract String [] getInterfaceDescriptors ();

    //

    public FieldInfo getDeclaredField (final String name) throws NoSuchFieldException {
        // Look among declared fields and throw an exception if it's not there.
        return __findField (name, _declaredFields ().unordered ()).orElseThrow (
            () -> new NoSuchFieldException (getCanonicalName () + "." + name)
        );
    }


    public FieldInfo [] getDeclaredFields () {
        return _declaredFields ().toArray (FieldInfo []::new);
    }

    //

    public FieldInfo getField (final String fieldName) throws NoSuchFieldException {
        // Look among public declared fields, try the super class if not found.
        final Optional <FieldInfo> result = __findField (
            fieldName, __publicFields (_declaredFields ().unordered ())
        );

        if (result.isPresent ()) {
            return result.get ();
        }

        if (getSuperclass () == null) {
            throw new NoSuchFieldException (getCanonicalName () + "." + fieldName);
        }

        return getSuperclass ().getField (fieldName);
    }


    public FieldInfo [] getFields () {
        // Collect all public fields along the inheritance hierarchy.
        return __fieldsToArray (_collectPublicFields (new ArrayList <> ()));
    }

    private static FieldInfo [] __fieldsToArray (final List <FieldInfo> fields) {
        return fields.toArray (new FieldInfo [fields.size ()]);
    }


    protected final List <FieldInfo> _collectPublicFields (final List <FieldInfo> result) {
        result.addAll (
            __publicFields (_declaredFields ()).collect (Collectors.toList ())
        );

        if (getSuperclass () != null) {
            getSuperclass ()._collectPublicFields (result);
        }

        return result;
    };

	//

    protected abstract Stream <FieldInfo> _declaredFields ();


    private Stream <FieldInfo> __publicFields (final Stream <FieldInfo> fields) {
        return fields.filter (fi -> fi.isPublic ());
    }


    private Optional <FieldInfo> __findField (final String fieldName, final Stream <FieldInfo> fields) {
        return fields.filter (fi -> fi.getName ().equals (fieldName)).findAny ();
    }

    //

    public MethodInfo getDeclaredMethod (
        final String methodName, final String [] paramDescriptors
    ) throws NoSuchMethodException {
        // Look among declared methods and throw an exception if it's not there.
        return __findMethod (
            methodName, paramDescriptors, _declaredMethods ().unordered ()
        ).orElseThrow (() -> new NoSuchMethodException (
            getCanonicalName () + "." + methodName + _descriptorsToString (paramDescriptors)
        ));
    }


    public MethodInfo getDeclaredMethod (
        final String methodName, final ShadowClass [] paramTypes
    ) throws NoSuchMethodException {
        return getDeclaredMethod (methodName, __toDescriptors (paramTypes));
    }


    public MethodInfo [] getDeclaredMethods () {
        return __withoutInitializers (_declaredMethods ()).toArray (MethodInfo []::new);
    }

	//

    public MethodInfo getMethod (
        final String methodName, final String [] paramDescriptors
    ) throws NoSuchMethodException {
        // Look among public declared methods, try the super class if not found.
        final Optional <MethodInfo> result = __findMethod (
            methodName, paramDescriptors,
            __publicMethods (__withoutInitializers (_declaredMethods ().unordered ()))
        );

        if (result.isPresent ()) {
            return result.get ();
        }

        if (getSuperclass () == null) {
            throw new NoSuchMethodException (
                getCanonicalName () + "." + methodName + _descriptorsToString (paramDescriptors)
            );
        }

        return getSuperclass ().getMethod (methodName, paramDescriptors);
    }


    public MethodInfo getMethod (
        final String methodName, final ShadowClass [] paramTypes
    ) throws NoSuchMethodException {
        return getMethod (methodName, __toDescriptors (paramTypes));
    }


    public MethodInfo [] getMethods () {
        // Collect all methods along the inheritance hierarchy.
        return _methodsToArray (_collectPublicMethods (new HashSet <> ()));
    }


    private static MethodInfo [] _methodsToArray (final Collection <MethodInfo> methods) {
        return methods.toArray (new MethodInfo [methods.size ()]);
    }


    protected final Set <MethodInfo> _collectPublicMethods (final Set <MethodInfo> result) {
        result.addAll (
            __publicMethods (__withoutInitializers (_declaredMethods ()))
                .collect (Collectors.toSet ())
        );

        if (getSuperclass () != null) {
            getSuperclass ()._collectPublicMethods (result);
        }

        return result;
    };

    //

    protected abstract Stream <MethodInfo> _declaredMethods ();


    private Stream <MethodInfo> __withoutInitializers (final Stream <MethodInfo> methods) {
        return methods.filter (mi-> !mi.isConstructor () && !mi.isClassInitializer ());
    }


    private Stream <MethodInfo> __publicMethods (final Stream <MethodInfo> methods) {
        return methods.filter (mi-> mi.isPublic ());
    }


    private Optional <MethodInfo> __findMethod (
        final String name, final String [] paramDescriptors,
        final Stream <MethodInfo> methods
    ) {
        return methods.filter (mi -> mi.matches (name,  paramDescriptors)).findAny ();
    }

	//

    /**
     * Returns type descriptors corresponding to declared inner classes, or an
     * empty array if this class does not declare any inner classes.
     *
     * @return an array of inner class type descriptors
     *
     * @see Class#getDeclaredClasses()
     */
    public String [] getDeclaredClassDescriptors () {
        // By default, a class declares no inner classes.
        return new String [0];
    }

	//

    private static String [] __toDescriptors (final ShadowClass [] types) {
        if (types == null) {
            return new String [0];
        }

        return Arrays.stream (types).map (ShadowClass::getName).toArray (String []::new);
    }


    protected static String _descriptorsToString (final String [] descriptors) {
        return Arrays.stream (descriptors).collect (
            Collectors.joining (", ",  "(",  ")")
        );
    }

    //

    @Override
    public void formatTo (
        final Formatter formatter,
        final int flags, final int width, final int precision
    ) {
        // FIXME LB: ShadowClass instances do not have a ShadowClass (of Class)
        formatter.format ("java.lang.Class@%d <%s>", getId (), getName ());
    }

}
