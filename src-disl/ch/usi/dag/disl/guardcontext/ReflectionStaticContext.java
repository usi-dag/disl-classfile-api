package ch.usi.dag.disl.guardcontext;
import ch.usi.dag.disl.Reflection;
import ch.usi.dag.disl.Reflection.Class;
import ch.usi.dag.disl.Reflection.Method;
import ch.usi.dag.disl.staticcontext.AbstractStaticContext;

import java.lang.classfile.MethodModel;


/**
 * Provides access to reflective information about the currently instrumented
 * method.
 * <p>
 * <b>Note:</b> This static context can only be used in guard methods, because
 * its methods return other than primitive types and strings, which is required
 * for using a static context in a snippet.
 */
public final class ReflectionStaticContext extends AbstractStaticContext {

    public Class thisClass () {
        return Reflection.systemClassLoader ().classForInternalName (
            staticContextData.getClassModel().thisClass().asInternalName()
        ).get ();
    }

    public Method thisMethod () {
        final MethodModel mn = staticContextData.getMethodModel();
        return thisClass().methodForSignature (mn.methodName().stringValue() + mn.methodTypeSymbol().descriptorString()).get ();
    }

}
