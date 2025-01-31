package ch.usi.dag.dislreserver.shadow;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.experimental.theories.ParameterSignature;
import org.junit.experimental.theories.ParameterSupplier;
import org.junit.experimental.theories.ParametersSuppliedBy;
import org.junit.experimental.theories.PotentialAssignment;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;
import org.objectweb.asm.Type;


@RunWith (Theories.class)
public class LambdaShadowClassTest extends ShadowClassTestBase {

    private static final AtomicLong __uniqueId__ = new AtomicLong (1);

    private static final HashMap <Class <?>, ShadowClass> __classCache__ = new HashMap <> ();

    //

    public static class LambdaTypeSupplier extends ParameterSupplier {
        @Override
        public List <PotentialAssignment> getValueSources (final ParameterSignature sig) {
            return _createClassAssignments (new Class <?> [] {
                ((IntFunction <String>) Integer::toString).getClass (),
                ((Comparator <String>) String::compareTo).getClass (),
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
            type, t -> new LambdaShadowClass (
                __uniqueId__.getAndIncrement (),
                Type.getType (t), null, superclass
            )
        );
    }

    //

    @Override @Theory
    public void getNameMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getNameMatchesReflection (type);
    }


    @Override @Theory
    public void getSimpleNameMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getSimpleNameMatchesReflection (type);
    }


    @Override @Theory
    public void getCanonicalNameMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getCanonicalNameMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isPrimitiveMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isPrimitiveMatchesReflection (type);
    }


    @Override @Theory
    public void isArrayMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isArrayMatchesReflection (type);
    }


    @Override @Theory
    public void isEnumMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isEnumMatchesReflection (type);
    }


    @Override @Theory
    public void isInterfaceMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isInterfaceMatchesReflection (type);
    }


    @Override @Theory
    public void isAnnotationMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isAnnotationMatchesReflection (type);
    }


    @Override
    @Theory
    public void isSyntheticMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isSyntheticMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getModifiersMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getModifiersMatchesReflection (type);
    }

    //

    @Override @Theory
    public void isInstanceOnSelfMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isInstanceOnSelfMatchesReflection (type);
    }


    @Override @Theory
    public void isAssignableOnSelfMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.isAssignableOnSelfMatchesReflection (type);
    }

    //

    @Override @Theory @Ignore
    public void getInterfaceDescriptorsMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        // TODO Enable when we have full information about lambda types.
        super.getInterfaceDescriptorsMatchesReflection (type);
    }

    //

    @Override @Theory
    public void getDeclaredFieldsMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getDeclaredFieldsMatchesReflection (type);
    }


    @Override @Theory
    public void getFieldsMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        super.getFieldsMatchesReflection (type);
    }

    //

    @Override @Theory @Ignore
    public void getDeclaredMethodsMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        // TODO Enable when we have full information about lambda types.
        super.getDeclaredMethodsMatchesReflection (type);
    }


    @Override @Theory @Ignore
    public void getMethodsMatchesReflection (@ParametersSuppliedBy (LambdaTypeSupplier.class) final Class <?> type) {
        // TODO Enable when we have full information about lambda types.
        super.getMethodsMatchesReflection (type);
    }

}
