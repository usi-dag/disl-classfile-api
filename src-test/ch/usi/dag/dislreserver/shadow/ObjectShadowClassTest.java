package ch.usi.dag.dislreserver.shadow;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Assert;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.ParametersSuppliedBy;
import org.junit.experimental.theories.PotentialAssignment;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.objectweb.asm.Type;


@RunWith (Theories.class)
public class ObjectShadowClassTest extends ShadowClassTestBase {

    private static final AtomicLong __uniqueId__ = new AtomicLong (1);

    private static final HashMap <Class <?>, ShadowClass> __classCache__ = new HashMap <> ();

    //

    static class ClassA {
        public static int fieldA;
        public void methodA () { };
    }

    static class ClassB extends ClassA {
        public int fieldB;
        public void methodB () { };
    }

    static class ClassC extends ClassB {
        public int fieldC;
        public void methodC () { };

        int fieldD;
        void methodD () { };
    }

    //

    public static class ReferenceTypeSupplier extends ParameterSupplier {
        @Override
        public List <PotentialAssignment> getValueSources (final ParameterSignature sig) {
            return _createClassAssignments (new Class <?> [] {
                Object.class, Byte.class, ClassC.class
            });
        }
    }

    //

    @Override
    protected ShadowClass _createShadowClass (final Class <?> type) {
        Assert.assertTrue (!type.isPrimitive () && !type.isArray ());

        final ShadowClass result = __classCache__.get (type);
        if (result != null) {
            return result;
        }

        final Class <?> superclass = type.getSuperclass ();
        if (superclass != null) {
                return __newShadowClass (type, _createShadowClass (superclass));
        }

        return __newShadowClass (type, null);
    }


    private static ShadowClass __newShadowClass (
        final Class <?> type, final ShadowClass superclass
    ) {
        return __classCache__.computeIfAbsent (
            type, t -> new ObjectShadowClass (
                __uniqueId__.getAndIncrement (),
                Type.getType (t), null, superclass,
                createClassNode (t)
            )
        );
    }

    //

    @Override @Theory
    public void getNameMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getNameMatchesReflection (type);
    }


    @Override @Theory
    public void getSimpleNameMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getSimpleNameMatchesReflection (type);
    }


    @Override @Theory
    public void getCanonicalNameMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getCanonicalNameMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isPrimitiveMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isPrimitiveMatchesReflection (type);
    }


    @Override @Theory
    public void isArrayMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isArrayMatchesReflection (type);
    }


    @Override @Theory
    public void isEnumMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isEnumMatchesReflection (type);
    }


    @Override @Theory
    public void isInterfaceMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isInterfaceMatchesReflection (type);
    }


    @Override @Theory
    public void isAnnotationMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isAnnotationMatchesReflection (type);
    }


    @Override
    @Theory
    public void isSyntheticMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isSyntheticMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getModifiersMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getModifiersMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isInstanceOnSelfMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isInstanceOnSelfMatchesReflection (type);
    }


    @Override @Theory
    public void isAssignableOnSelfMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.isAssignableOnSelfMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getInterfaceDescriptorsMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getInterfaceDescriptorsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredFieldsMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getDeclaredFieldsMatchesReflection (type);
    }


    @Override @Theory
    public void getFieldsMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getFieldsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredMethodsMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getDeclaredMethodsMatchesReflection (type);
    }


    @Override @Theory
    public void getMethodsMatchesReflection (@ParametersSuppliedBy (ReferenceTypeSupplier.class) final Class <?> type) {
        super.getMethodsMatchesReflection (type);
    }

}
