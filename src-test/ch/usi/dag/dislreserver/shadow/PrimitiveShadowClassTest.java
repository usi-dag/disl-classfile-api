package ch.usi.dag.dislreserver.shadow;

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
public class PrimitiveShadowClassTest extends ShadowClassTestBase {

    private static final AtomicLong __uniqueId__ = new AtomicLong (1);

    //

    public static class PrimitiveTypeSupplier extends ParameterSupplier {
        @Override
        public List <PotentialAssignment> getValueSources (final ParameterSignature sig) {
            return _createClassAssignments (new Class <?> [] {
                void.class, boolean.class, byte.class, char.class,
                short.class, int.class, long.class, float.class, double.class
            });
        }
    }

    @Override
    protected ShadowClass _createShadowClass (final Class <?> type) {
        Assert.assertTrue (type.isPrimitive ());

        return new PrimitiveShadowClass (
            __uniqueId__.getAndIncrement (), Type.getType (type), null
        );
    }

    //

    @Override @Theory
    public void getNameMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.getNameMatchesReflection (type);
    }


    @Override @Theory
    public void getSimpleNameMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.getSimpleNameMatchesReflection (type);
    }


    @Override @Theory
    public void getCanonicalNameMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.getCanonicalNameMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isPrimitiveMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isPrimitiveMatchesReflection (type);
    }


    @Override @Theory
    public void isArrayMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isArrayMatchesReflection (type);
    }


    @Override @Theory
    public void isEnumMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isEnumMatchesReflection (type);
    }


    @Override @Theory
    public void isInterfaceMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isInterfaceMatchesReflection (type);
    }


    @Override @Theory
    public void isAnnotationMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isAnnotationMatchesReflection (type);
    }


    @Override
    @Theory
    public void isSyntheticMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isSyntheticMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getModifiersMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.getModifiersMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isInstanceOnSelfMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isInstanceOnSelfMatchesReflection (type);
    }


    @Override @Theory
    public void isAssignableOnSelfMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        super.isAssignableOnSelfMatchesReflection (type);
    }

    //

    @Theory
    public void getSuperclassMatchesReflection (
        @ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type
    ) {
        // Primitive type has no super class, i.e., super class is null.
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertSame (type.getSuperclass (), shadowType.getSuperclass ());
    }


    @Theory
    public void primitiveTypeHasNoInterfaces (
        @ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type
    ) {
        final ShadowClass shadowType = _createShadowClass (type);
        Assert.assertArrayEquals (new ShadowClass [0], shadowType.getInterfaces ());
    }


    @Override @Theory
    public void getInterfaceDescriptorsMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        // Primitive type implements no interfaces -> no descriptors.
        super.getInterfaceDescriptorsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredFieldsMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        // Primitive type has no declared fields.
        super.getDeclaredFieldsMatchesReflection (type);
    }


    @Override @Theory
    public void getFieldsMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        // Primitive type has no declared or inherited public fields.
        super.getFieldsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredMethodsMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        // Primitive type has no declared methods.
        super.getDeclaredMethodsMatchesReflection (type);
    }


    @Override @Theory
    public void getMethodsMatchesReflection (@ParametersSuppliedBy (PrimitiveTypeSupplier.class) final Class <?> type) {
        // Primitive type has no declared or inherited public methods.
        super.getMethodsMatchesReflection (type);
    }

}
