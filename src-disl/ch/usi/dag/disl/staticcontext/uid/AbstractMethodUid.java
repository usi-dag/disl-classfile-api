package ch.usi.dag.disl.staticcontext.uid;

import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;
import java.lang.classfile.ClassModel;


public abstract class AbstractMethodUid extends AbstractUniqueId {

    private static AbstractUniqueId __instance__;

    //

    // constructor for static context
    public AbstractMethodUid () {
        super ();
    }

    // constructor for singleton
    protected AbstractMethodUid (
        final AbstractIdCalculator idCalc, final String outputFileName
    ) {
        super (idCalc, outputFileName);
    }

    //

    @Override
    protected final String idFor () {
        final ClassModel classNode = staticContextData.getClassModel();
        final MethodModelCopy methodNode = staticContextData.getMethodModel();

        return JavaNames.methodUniqueName (classNode.thisClass().name().stringValue(), methodNode.methodName().stringValue(), methodNode.methodTypeSymbol().descriptorString());
    }

    @Override
    protected final synchronized AbstractUniqueId getSingleton () {
        if (__instance__ == null) {
            __instance__ = _getInstance ();
        }

        return __instance__;
    }

    //

    protected abstract AbstractUniqueId _getInstance ();

}
