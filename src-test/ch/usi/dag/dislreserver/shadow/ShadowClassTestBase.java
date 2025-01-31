package ch.usi.dag.dislreserver.shadow;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.experimental.theories.PotentialAssignment;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import ch.usi.dag.util.asm.ClassNodeHelper;


abstract class ShadowClassTestBase {

    protected static List <PotentialAssignment> _createClassAssignments (
        final Class <?> [] types
    ) {
        return Arrays.stream (types)
            .map (type -> PotentialAssignment.forValue (type.getName (), type))
            .collect (Collectors.toList ());
    }


    protected abstract ShadowClass _createShadowClass (final Class <?> type);

    /**
     * Removes ASM specific access flags not defined in the
     * JVM specification. This is necessary to compare regular and
     * shadow info.
     * 
     * JVM access flags are stored using 16 bits only, so it is just
     * possible to apply a mask.
     */
    private int removeASMSpecificAccessFlags(int modifiers) {
        return modifiers & 65535;
    }

    //

    void getNameMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.getName (), shadowType.getName ());
    }


    void getSimpleNameMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.getSimpleName (), shadowType.getSimpleName ());
    }


    void getCanonicalNameMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.getCanonicalName (), shadowType.getCanonicalName ());
    }

    //

    void isPrimitiveMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isPrimitive (), shadowType.isPrimitive ());
    }


    void isArrayMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isArray (), shadowType.isArray ());
    }


    void isEnumMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isEnum (), shadowType.isEnum ());
    }


    void isInterfaceMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isInterface (), shadowType.isInterface ());
    }


    void isAnnotationMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isAnnotation (), shadowType.isAnnotation ());
    }


    void isSyntheticMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (type.isSynthetic (), shadowType.isSynthetic ());
    }

    //

    void getModifiersMatchesReflection (final Class <?> type) {
        //
        // Testing modifiers on normal classes seems problematic, because some
        // of the modifiers reported by reflection are not written in the class
        // byte code. So we at least test that all (valid) modifiers found in
        // the byte code are set on the reflection class.
        //

        final ShadowClass shadowType = _createShadowClass (type);
        final int modifiers = shadowType.getModifiers ();
        Assert.assertEquals (type.getModifiers () & modifiers, modifiers);
    }

    //

    void isInstanceOnSelfMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (
            type.isInstance (type),
            shadowType.isInstance (shadowType)
        );
    }


    void isAssignableOnSelfMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertEquals (
            type.isAssignableFrom (type),
            shadowType.isAssignableFrom (shadowType)
        );
    }

    //

    void getInterfaceDescriptorsMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);

        Assert.assertArrayEquals (
            __sortedStringArray (Arrays.stream (type.getInterfaces ()).map (c -> Type.getType (c).getDescriptor ())),
            __sortedStringArray (Arrays.stream (shadowType.getInterfaceDescriptors ()))
        );
    }

    private static String [] __sortedStringArray (final Stream <String> strings) {
        return strings.sorted (String::compareTo).toArray (String []::new);
    }

    //

    void getDeclaredFieldsMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        __assertFieldArraysEqual (
            type.getDeclaredFields (), shadowType.getDeclaredFields ()
        );
    }


    void getFieldsMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        __assertFieldArraysEqual (type.getFields (), shadowType.getFields ());
    }


    private void __assertFieldArraysEqual (
        final Field [] fields, final FieldInfo [] shadowFields
    ) {
        Assert.assertEquals (fields.length, shadowFields.length);

        Arrays.sort (fields, (a, b) -> a.getName ().compareTo (b.getName ()));
        Arrays.sort (shadowFields, (a, b) -> a.getName ().compareTo (b.getName ()));

        for (int i = 0; i < fields.length; i++) {
            __assertEquals (fields [i], shadowFields [i]);
        }
    }


    private void __assertEquals (final Field field, final FieldInfo shadowField) {
        Assert.assertEquals (field.getName (), shadowField.getName ());
        Assert.assertEquals (Type.getType (field.getType ()).getDescriptor (), shadowField.getDescriptor ());
        Assert.assertEquals (field.getModifiers (), removeASMSpecificAccessFlags(shadowField.getModifiers ()));
    }

    //

    void getDeclaredMethodsMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        __assertMethodArraysEqual (
            type.getDeclaredMethods (), shadowType.getDeclaredMethods ()
        );
    }


    void getMethodsMatchesReflection (final Class <?> type) {
        final ShadowClass shadowType = _createShadowClass (type);
        __assertMethodArraysEqual (type.getMethods (), shadowType.getMethods ());
    }


    private static final Comparator <? super Method> __methodComparator__ = (a, b) -> {
        //
        // Order methods by names, break ties using method descriptors.
        //
        final int nameResult = a.getName ().compareTo (b.getName ());
        if (nameResult != 0) {
            return nameResult;
        }

        return Type.getMethodDescriptor (a).compareTo (Type.getMethodDescriptor (b));
    };


    private static final Comparator <? super MethodInfo> __shadowMethodComparator__ = (a, b) -> {
        //
        // Order methods by names, break ties using method descriptors.
        //
        final int nameResult = a.getName ().compareTo (b.getName ());
        if (nameResult != 0) {
            return nameResult;
        }

        return a.getDescriptor ().compareTo (b.getDescriptor ());
    };


    private void __assertMethodArraysEqual (
        final Method [] methods, final MethodInfo [] shadowMethods
    ) {
        Assert.assertEquals (methods.length, shadowMethods.length);

        Arrays.sort (methods, __methodComparator__);
        Arrays.sort (shadowMethods, __shadowMethodComparator__);

        for (int i = 0; i < methods.length; i++) {
            __assertEqual (methods [i], shadowMethods [i]);
        }
    }


    private void __assertEqual (final Method method, final MethodInfo shadowMethod) {
        Assert.assertEquals (method.getName (), shadowMethod.getName ());
        Assert.assertEquals (Type.getMethodDescriptor (method), shadowMethod.getDescriptor ());
        Assert.assertEquals (method.getModifiers (), removeASMSpecificAccessFlags(shadowMethod.getModifiers ()));
    }

    //

    static ClassNode createClassNode (final Class <?> type) {
        try {
            return ClassNodeHelper.OUTLINE.load (type.getName ());

        } catch (final IOException ioe) {
            throw new RuntimeException ("could not load byte code for "+ type.getName (), ioe);
        }
    }

    //
    // The following methods are intended for debugging purposes.
    //

    static void _printClassInfo (
        final Class <?> type, final PrintStream out
    ) {
        out.printf ("%08x [%s] %s\n", type.getModifiers (), Modifier.toString (type.getModifiers ()), type.getName ());

        out.printf ("\tsuperclass: %s\n", type.getSuperclass ());

        out.printf ("\tinterfaces:\n");
        Arrays.stream (type.getInterfaces ()).forEach (
            c -> out.printf ("\t\t%s, %s\n", c.getName (), Type.getType (c).getDescriptor ())
        );

        out.printf ("\tdeclared fields:\n");
        __printFields (type.getDeclaredFields (), out);

        out.printf ("\tfields:\n");
        __printFields (type.getFields (), out);

        out.printf ("\tconstructors:\n");
        __printConstructors (type.getConstructors (), out);

        out.printf ("\tdeclared methods:\n");
        __printMethods (type.getDeclaredMethods (), out);

        out.printf ("\tmethods:\n");
        __printMethods (type.getMethods (), out);
    }


    private static void __printFields (final Field [] fields, final PrintStream out) {
        Arrays.stream (fields).forEach (f -> out.printf (
            "\t\t%08x=[%s] %s %s\n",
            f.getModifiers (), Modifier.toString (f.getModifiers ()),
            f.getType ().getCanonicalName (), f.getName ())
        );
    }


    private static void __printConstructors (final Constructor <?> [] ctors, final PrintStream out) {
        Arrays.stream (ctors).forEach (c -> out.printf (
            "\t\t%08x=[%s] %s (%s)\n",
            c.getModifiers (), Modifier.toString (c.getModifiers ()), c.getName (),
            __parameterList (c.getParameterTypes ())
        ));
    }


    private static void __printMethods (final Method [] methods, final PrintStream out) {
        Arrays.stream (methods).forEach (m -> out.printf (
            "\t\t%08x=[%s] %s %s (%s)\n",
            m.getModifiers (), Modifier.toString (m.getModifiers ()),
            m.getReturnType ().getCanonicalName (), m.getName (),
            __parameterList (m.getParameterTypes ())
        ));
    }


    private static String __parameterList (final Class <?> [] types) {
        return Arrays.stream (types)
            .map (pt -> pt.getCanonicalName())
            .collect (Collectors.joining (","));
    }

}
