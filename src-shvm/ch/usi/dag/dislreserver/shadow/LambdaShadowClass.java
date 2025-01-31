package ch.usi.dag.dislreserver.shadow;

import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import ch.usi.dag.dislreserver.util.Env;


final class LambdaShadowClass extends ShadowClass {

    private final ShadowClass __superClass;

    //

    LambdaShadowClass (
        final long netReference, final Type type,
        final ShadowObject classLoader, final ShadowClass superClass
    ) {
        super (netReference, type, classLoader);

        __superClass = superClass;
    }

    //

    @Override
    public String getName () {
        return __canonicalName ();
    }


    @Override
    public String getSimpleName () {
        return _simpleName (__canonicalName ());
    }


    @Override
    public String getCanonicalName () {
        //
        // From Java 15, lambdas are treated as Hidden Classes
        //
        if (Env.getJavaVersion() > 14) {
            return null;
        }
        
        return __canonicalName ();
    }


    private String __canonicalName () {
        //
        // Avoid Type.getClassName() because it converts all slashes to dots,
        // instead of only those preceding the $$Lambda$ suffix. Also, the dollars
        // in the lambda type canonical names should not be converted to dots.
        //
        final String name = _type ().getInternalName ();
        final int start = name.lastIndexOf ("$$Lambda$");
        assert start > 0;

        return _javaName (name.substring (0, start)).concat (name.substring (start));
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
        return this.equals (other);
    }

    //

    @Override
    public int getModifiers () {
        //
        // Lambda classes are SYNTHETIC and FINAL.
        //
        return Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL;
    }

    //

    @Override
    public ShadowClass getSuperclass () {
        // Lambda types have Object as the super class.
        return __superClass;
    }


    @Override
    public ShadowClass [] getInterfaces () {
        throw new UnsupportedOperationException ("not yet implemented");
    }


    @Override
    public String [] getInterfaceDescriptors () {
        throw new UnsupportedOperationException ("not yet implemented");
    }

    //

    @Override
    protected Stream <FieldInfo> _declaredFields () {
        // Lambda types have no declared fields.
        return Stream.empty ();
    }


    @Override
    protected Stream <MethodInfo> _declaredMethods () {
        throw new UnsupportedOperationException ("not yet implemented");
    }

}
