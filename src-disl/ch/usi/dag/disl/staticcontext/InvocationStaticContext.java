package ch.usi.dag.disl.staticcontext;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import ch.usi.dag.disl.marker.BytecodeMarker;
import ch.usi.dag.disl.util.Insn;
import ch.usi.dag.disl.util.JavaNames;


/**
 * Represents a static context for method invocations. Provides information
 * related to a method invocation at a particular call site.
 * <p>
 * <b>Note:</b> This context can only be used with the {@link BytecodeMarker}
 * triggering on method invocation instructions, i.e., INVOKESTATIC,
 * INVOKEVIRTUAL, INVOKESPECIAL, and INVOKEINTERFACE. If you are not sure
 * whether the context can be used, use the {@link #isValid()} method to check
 * if the context is valid.
 *
 * @author Aibek Sarimbekov
 * @author Lubomir Bulej
 */
public class InvocationStaticContext extends AbstractStaticContext {

    public InvocationStaticContext () {
        // invoked by DiSL
    }

    /**
     * @return {@code True} if the context is valid.
     */
    public boolean isValid () {
        return staticContextData.getRegionStart () instanceof MethodInsnNode;
    }

    //

    /**
     * Returns the name of the method being invoked. This name includes just the
     * name of the method, no class name or parameter type descriptor.
     *
     * @return The name of the method being invoked.
     */
    public String getName () {
        return __methodName ();
    }


    /**
     * Returns a fully qualified internal name of the method being invoked. This
     * name includes the internal name of the class owning the method and the
     * name of method itself, but does not include the type descriptor.
     *
     * @return Fully qualified internal name of the method being invoked.
     */
    public String getInternalName () {
        return JavaNames.methodName (__methodOwner (), __methodName ());
    }


    /**
     * Returns a fully qualified unique internal name of the method being
     * invoked. This name includes the internal name of the class owning the
     * method, the name of the method itself, and the type descriptor.
     *
     * @return Fully qualified unique internal name of the method being invoked.
     */
    public String getUniqueInternalName () {
        return JavaNames.methodUniqueName (
            __methodOwner (), __methodName (), __descriptor ()
        );
    }


    /**
     * @return The type descriptor of the method being invoked.
     */
    public String getDescriptor () {
        return __descriptor ();
    }


    /**
     * @return The return type descriptor of the method being invoked.
     */
    public String getReturnTypeDescriptor () {
        return Type.getReturnType (__descriptor ()).getDescriptor ();
    }

    //

    /**
     * @return The type name of the class owning the method being invoked, i.e.,
     *         a fully qualified class name, with packages delimited by the '.'
     *         character.
     */
    public String getOwnerName () {
        return JavaNames.internalToType (__methodOwner ());
    }

    /**
     * @return The simple name of the class owning the method being invoked,
     *         i.e., a class name without the package part of the name.
     */
    public String getOwnerSimpleName () {
        return JavaNames.simpleClassName (__methodOwner ());
    }

    /**
     * @return The internal name of the class owning the method being invoked.
     */
    public String getOwnerInternalName () {
        return __methodOwner ();
    }

    //

    /**
     * @return {@code true} if this is a special method invocation (i.e., a
     *         constructor).
     */
    public boolean isSpecial () {
        return __isOpcode (Insn.INVOKESPECIAL);
    }


    /**
     * @return {@code true} if this is a virtual method invocation.
     */
    public boolean isVirtual () {
        return __isOpcode (Insn.INVOKEVIRTUAL);
    }


    /**
     * @return {@code true} if this is an interface method invocation.
     */
    public boolean isInterface () {
        return __isOpcode (Insn.INVOKEINTERFACE);
    }


    /**
     * @return {@code true} if this is a static method invocation.
     */
    public boolean isStatic () {
        return __isOpcode (Insn.INVOKESTATIC);
    }


    /**
     * @return {@code true} if this is a dynamic invocation.
     */
    public boolean isDynamic () {
        return __isOpcode (Insn.INVOKEDYNAMIC);
    }


    private boolean __isOpcode (final Insn insn) {
        return insn.equals (Insn.forOpcode (__methodInsnNode ().getOpcode ()));
    }

    //

    /**
     * Returns {@code true} if this method is a constructor.
     */
    public boolean isConstructor () {
        return JavaNames.isConstructorName (__methodName ());
    }


    /**
     * Returns {@code true} if this method is a class initializer.
     */
    public boolean isInitializer () {
        return JavaNames.isInitializerName (__methodName ());
    }

    //

    private String __methodOwner () {
        return __methodInsnNode ().owner;
    }

    private String __methodName () {
        return __methodInsnNode ().name;
    }


    private String __descriptor () {
        return __methodInsnNode ().desc;
    }


    private MethodInsnNode __methodInsnNode () {
        //
        // This will throw an exception when used in a region that does not
        // start with a method invocation instruction.
        //
        return ((MethodInsnNode) staticContextData.getRegionStart ());
    }

}
