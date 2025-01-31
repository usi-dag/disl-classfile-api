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
public class ArrayShadowClassTest extends ShadowClassTestBase {

    private static final AtomicLong __uniqueId__ = new AtomicLong (1);

    private static final ShadowClass __objectShadowClass__ = new ObjectShadowClass (
        __uniqueId__.getAndIncrement (), Type.getType (Object.class),
        null /* no class loader */, null /* no super class */,
        createClassNode (Object.class)
    );

    //

    public static class ArrayTypeSupplier extends ParameterSupplier {
        @Override
        public List <PotentialAssignment> getValueSources (final ParameterSignature sig) {
            return _createClassAssignments (new Class <?> [] {
                // primitive array types
                boolean [].class, byte [].class, char [].class, short [].class,
                int [].class, long [].class, float [].class, double [].class,

                // reference array types
                boolean [][].class, Boolean [].class
            });
        }

    }

    //

    @Override
    protected ShadowClass _createShadowClass (final Class <?> type) {
        Assert.assertTrue (type.isArray ());

        return new ArrayShadowClass (
            __uniqueId__.getAndIncrement (), Type.getType (type),
            null /* no class loader */, __objectShadowClass__,
            null /* no component type yet */
        );
    }

    //

    @Override @Theory
    public void getNameMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.getNameMatchesReflection (type);
    }


    @Override @Theory
    public void getSimpleNameMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.getSimpleNameMatchesReflection (type);
    }


    @Override @Theory
    public void getCanonicalNameMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.getCanonicalNameMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isPrimitiveMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isPrimitiveMatchesReflection (type);
    }


    @Override @Theory
    public void isArrayMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isArrayMatchesReflection (type);
    }


    @Override @Theory
    public void isEnumMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isEnumMatchesReflection (type);
    }


    @Override @Theory
    public void isInterfaceMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isInterfaceMatchesReflection (type);
    }


    @Override @Theory
    public void isAnnotationMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isAnnotationMatchesReflection (type);
    }


    @Override @Theory
    public void isSyntheticMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isSyntheticMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getModifiersMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.getModifiersMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isInstanceOnSelfMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isInstanceOnSelfMatchesReflection (type);
    }


    @Override @Theory
    public void isAssignableOnSelfMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.isAssignableOnSelfMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getInterfaceDescriptorsMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        super.getInterfaceDescriptorsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredFieldsMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        // Array types have no declared fields.
        super.getDeclaredFieldsMatchesReflection (type);
    }


    @Override @Theory
    public void getFieldsMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        // Array types have no declared or inherited public fields.
        super.getFieldsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredMethodsMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        // Array type has no declared methods.
        super.getDeclaredMethodsMatchesReflection (type);
    }


    @Override @Theory
    public void getMethodsMatchesReflection (@ParametersSuppliedBy (ArrayTypeSupplier.class) final Class <?> type) {
        // Array types inherit public methods from java.lang.Object.
        super.getMethodsMatchesReflection (type);
    }

}
