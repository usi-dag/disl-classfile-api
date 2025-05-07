package ch.usi.dag.dislreserver.shadow;

import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.stream.Stream;


import ch.usi.dag.disl.util.ClassFileHelper;
import ch.usi.dag.dislreserver.util.Env;


final class LambdaShadowClass extends ShadowClass {

    private final ShadowClass __superClass;

    //
    private final String extension;

    LambdaShadowClass (
        final long netReference, final String typeDescriptor,
        final ShadowObject classLoader, final ShadowClass superClass
    ) {

        ClassDesc type;
        String ex = "";
        try {
            type = ClassDesc.ofDescriptor(typeDescriptor);
        } catch (Exception _) {
            type = ClassDesc.ofDescriptor(__fixLambdaDesc(typeDescriptor));
            ex = __getExtraPart(typeDescriptor);
        }
        extension = ex;

        super (netReference, type, classLoader);

        __superClass = superClass;
    }

    private static String __fixLambdaDesc(String descriptor) {
        // this should remove the last part of descriptor like: Lch/usi/dag/dislreserver/shadow/LambdaShadowClassTest$LambdaTypeSupplier$$Lambda.0x00000fe001018000;
        // but leaving the last ";"
        String regex = "\\.[0-9]x[0-9a-z]+";
        return descriptor.replaceFirst(regex, "");
    }

    private static String __getExtraPart(String descriptor) {
        // this should return the last part of the descriptor like: Lch/usi/dag/dislreserver/shadow/LambdaShadowClassTest$LambdaTypeSupplier$$Lambda.0x00000fe001018000;
        // would return: 0x00000fe001018000 without the dot and the ';'.
        return descriptor.substring(descriptor.lastIndexOf(".") + 1, descriptor.length() -1);
    }

    @Override
    public String getName () {
        if (extension.isEmpty()) {
            return __canonicalName();
        }
        return __canonicalName() + "/" + extension;
    }


    @Override
    public String getSimpleName () {
        if (extension.isEmpty()) {
            return _simpleName(__canonicalName());
        }
        return _simpleName(__canonicalName()) + "/" + extension;
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

        final String name = ClassFileHelper.getInternalName(_type());
        final int start = name.lastIndexOf ("$$Lambda");
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
    public List<AccessFlag> getModifiers () {
        //
        // Lambda classes are SYNTHETIC and FINAL. SUPER was also present in some tests
        //
        return List.of(AccessFlag.SYNTHETIC, AccessFlag.FINAL, AccessFlag.SUPER);
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
