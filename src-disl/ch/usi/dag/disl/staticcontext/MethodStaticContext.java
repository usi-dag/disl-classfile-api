package ch.usi.dag.disl.staticcontext;

import ch.usi.dag.disl.util.JavaNames;
import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.*;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;


/**
 * Provides method related static context information for the method being
 * instrumented.
 */
public class MethodStaticContext extends AbstractStaticContext {

    /**
     * Returns the internal name of the instrumented class, i.e., a fully
     * qualified class name, with packages delimited by the '/' character.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getInternalName()} instead.
     */
    @Deprecated
    public String thisClassName () {
        return __classInternalName ();
    }


    /**
     * Returns the simple name of the instrumented class, i.e., a class name
     * without the package part of the name.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getSimpleName()} instead.
     */
    @Deprecated
    public String thisClassSimpleName () {
        return JavaNames.simpleClassName (__classInternalName ());
    }


    /**
     * Returns the canonical name of the instrumented class, i.e., a fully
     * qualified class name, with packages delimited by the '.' character.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getName()} instead.
     */
    @Deprecated
    public String thisClassCanonicalName () {
        return JavaNames.internalToType (__classInternalName ());
    }


    /**
     * Returns the internal name of the class enclosing the instrumented class,
     * or empty string if the instrumented class is not enclosed in another
     * class.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getOuterClassInternalName()} instead.
     */
    @Deprecated
    public String thisClassOuterClass () {
        Optional<NestHostAttribute> attribute = __classNode().findAttribute(Attributes.nestHost());
        if (attribute.isEmpty()) {
            return "";
        }
        return attribute.get().nestHost().asInternalName();
    }


    /**
     * Returns the name of the method enclosing the instrumented class, or
     * empty string if the class is not enclosed in a method.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getOuterMethodName()} instead.
     */
    @Deprecated
    public String thisClassOuterMethod () {
        Optional<EnclosingMethodAttribute> attribute = __classNode().findAttribute(Attributes.enclosingMethod());
        if (attribute.isEmpty()) {
            return "";
        } else {
            Optional<NameAndTypeEntry> m = attribute.get().enclosingMethod();
            if (m.isEmpty()) {
                return "";
            }
            return m.get().name().stringValue();
        }
    }


    /**
     * Returns outer method descriptor of the instrumented class.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getOuterMethodDescriptor()} instead.
     */
    @Deprecated
    public String thisClassOuterMethodDesc () {
        Optional<EnclosingMethodAttribute> attribute = __classNode().findAttribute(Attributes.enclosingMethod());
        if (attribute.isEmpty()) {
            return "";
        } else {
            Optional<MethodTypeDesc> m = attribute.get().enclosingMethodTypeSymbol();
            if (m.isEmpty()) {
                return "";
            }
            return m.get().descriptorString();
        }
    }


    /**
     * Returns the signature of the instrumented class, or {@code null} if the
     * class is not a generic type.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getSignature()} instead.
     */
    @Deprecated
    public String thisClassSignature () {
        return Signature.of(__classNode().thisClass().asSymbol()).signatureString();
    }


    /**
     * Returns the name of the source file containing the instrumented class.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getSourceFile()} instead.
     */
    @Deprecated
    public String thisClassSourceFile () {
        Optional<SourceFileAttribute> attribute = __classNode().findAttribute(Attributes.sourceFile());
        if (attribute.isEmpty()) {
            return "";
        }
        return attribute.get().sourceFile().stringValue();
    }


    /**
     * Returns the internal name of the super class of the instrumented class,
     * i.e., a fully qualified class name, with package names delimited by the
     * '/' character.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#getSuperClassInternalName()} instead.
     */
    @Deprecated
    public String thisClassSuperName () {
        ClassModel thisClass = __classNode();
        if (thisClass.superclass().isEmpty()) {
            return "";
        } else {
            return thisClass.superclass().get().asInternalName();
        }
    }


    /**
     * Returns class minor version.
     * <p>
     */
    @Deprecated
    public int thisClassMinorVersion () {
        return __classNode ().minorVersion();
    }

    /**
     * Returns class major version.
     * <p>
     */
    public int thisClassMajorVersion() {
        return __classNode().majorVersion();
    }


    /**
     * Returns {@code true} if the instrumented class is abstract.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isAbstract()} instead.
     */
    @Deprecated
    public boolean isClassAbstract () {
        return __classAccessFlag (AccessFlag.ABSTRACT);
    }


    /**
     * Returns {@code true} if the instrumented class is an annotation.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isAnnotation()} instead.
     */
    @Deprecated
    public boolean isClassAnnotation () {
        return __classAccessFlag (AccessFlag.ANNOTATION);
    }


    /**
     * Returns {@code true} if the instrumented class is an enum.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isEnum()} instead.
     */
    @Deprecated
    public boolean isClassEnum () {
        return __classAccessFlag (AccessFlag.ENUM);
    }


    /**
     * Returns {@code true} if the instrumented class is final.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isFinal()} instead.
     */
    @Deprecated
    public boolean isClassFinal () {
        return __classAccessFlag (AccessFlag.FINAL);
    }


    /**
     * Returns {@code true} if the instrumented class is an interface.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isInterface()} instead.
     */
    @Deprecated
    public boolean isClassInterface () {
        return __classAccessFlag (AccessFlag.INTERFACE);
    }


    /**
     * Returns {@code true} if the instrumented class is private.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isPrivate()} instead.
     */
    @Deprecated
    public boolean isClassPrivate () {
        return __classAccessFlag (AccessFlag.PRIVATE);
    }


    /**
     * Returns {@code true} if the instrumented class is protected.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isProtected()} instead.
     */
    @Deprecated
    public boolean isClassProtected () {
        return __classAccessFlag(AccessFlag.PROTECTED);
    }


    /**
     * Returns {@code true} if the instrumented class is public.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isPublic()} instead.
     */
    @Deprecated
    public boolean isClassPublic () {
        return __classAccessFlag (AccessFlag.PUBLIC);
    }


    /**
     * Returns {@code true} if the instrumented class is synthetic.
     * <p>
     * <b>Note:</b> This method is being deprecated, please use
     * {@link ClassStaticContext#isSynthetic()} instead.
     */
    @Deprecated
    public boolean isClassSynthetic () {
        return __classAccessFlag (AccessFlag.SYNTHETIC);
    }


    // *** Method ***


    /**
     * Returns the plain name of this method. This name does includes neither
     * the class name, nor the parameter type descriptor.
     *
     * @return The name of this method.
     */
    public String thisMethodName () {
        return __methodName ();
    }


    /**
     * Returns the fully qualified (internal) name of the instrumented method,
     * i.e., including the (internal) name of the class containing the method.
     */
    public String thisMethodFullName () {
        return JavaNames.methodName (__classInternalName (), __methodName ());
    }


    /**
     * Returns the fully qualified (internal) name of the instrumented method,
     * i.e., including the (internal) name of the class containing the method.
     */
    public String getUniqueInternalName () {
        return JavaNames.methodUniqueName (
            __classInternalName (), __methodName (), __methodNode ().methodTypeSymbol().descriptorString()
        );
    }


    /**
     * Returns the descriptor of the instrumented method.
     */
    public String thisMethodDescriptor () {
        return __methodNode().methodTypeSymbol().descriptorString();
    }


    /**
     * Returns the generic signature of the instrumented method or {@code null}
     * if the method is not a generic method.
     */
    public String thisMethodSignature () {
        return MethodSignature.of(__methodNode().methodTypeSymbol()).signatureString();
    }


    /**
     * Returns {@code true} if this method is a constructor.
     */
    public boolean isMethodConstructor () {
        return JavaNames.isConstructorName (__methodName ());
    }


    /**
     * Returns {@code true} if this method is a class initializer.
     */
    public boolean isMethodInitializer () {
        return JavaNames.isInitializerName (__methodName ());
    }


    /**
     * Returns {@code true} if the instrumented method is a bridge.
     */
    public boolean isMethodBridge () {
        return __methodAccessFlag(AccessFlag.BRIDGE);
    }


    /**
     * Returns {@code true} if the instrumented method is final.
     */
    public boolean isMethodFinal () {
        return __methodAccessFlag(AccessFlag.FINAL);
    }


    /**
     * Returns {@code true} if the instrumented method is private.
     */
    public boolean isMethodPrivate () {
        return __methodAccessFlag (AccessFlag.PRIVATE);
    }


    /**
     * Returns {@code true} if the instrumented method is protected.
     */
    public boolean isMethodProtected () {
        return __methodAccessFlag(AccessFlag.PROTECTED);
    }


    /**
     * Returns {@code true} if the instrumented method is public.
     */
    public boolean isMethodPublic () {
        return __methodAccessFlag(AccessFlag.PUBLIC);
    }


    /**
     * Returns {@code true} if the instrumented method is static.
     */
    public boolean isMethodStatic () {
        return __methodAccessFlag(AccessFlag.STATIC);
    }


    /**
     * Returns {@code true} if the instrumented method is synchronized.
     */
    public boolean isMethodSynchronized () {
        return __methodAccessFlag(AccessFlag.SYNCHRONIZED);
    }


    /**
     * Returns {@code true} if the instrumented method accepts a variable number
     * of arguments.
     */
    public boolean isMethodVarArgs () {
        return __methodAccessFlag(AccessFlag.VARARGS);
    }


    //

    private String __classInternalName () {
        return __classNode().thisClass().asInternalName();
    }


    private boolean __classAccessFlag (final AccessFlag flag) {
        final AccessFlags flags = __classNode().flags();
        return flags.has(flag);
    }


    private ClassModel __classNode () {
        return staticContextData.getClassModel();
    }


    private String __methodName () {
        return __methodNode().methodName().stringValue();
    }


    private MethodModelCopy __methodNode () {
        return staticContextData.getMethodModel();
    }


    private boolean __methodAccessFlag (final AccessFlag flag) {
        final AccessFlags flags = __methodNode().flags();
        return flags.has(flag);
    }

}
