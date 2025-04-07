package ch.usi.dag.disl.staticcontext;

import ch.usi.dag.disl.util.JavaNames;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.EnclosingMethodAttribute;
import java.lang.classfile.attribute.NestHostAttribute;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;


/**
 * Provides class related static context information for the method being
 * instrumented.
 */
public class ClassStaticContext extends AbstractStaticContext {

    /**
     * Returns the type name of the instrumented class, i.e., a fully
     * qualified class name, with packages delimited by the '.' character.
     */
    public String getName () {
        return JavaNames.internalToType (__classInternalName ());
    }


    /**
     * Returns the internal name of the instrumented class, i.e., a fully
     * qualified class name, with packages delimited by the '/' character.
     */
    public String getInternalName () {
        return __classInternalName ();
    }


    /**
     * Returns the simple name of the instrumented class, i.e., a class name
     * without the package part of the name.
     */
    public String getSimpleName () {
        return JavaNames.simpleClassName (__classInternalName ());
    }


    /**
     * Returns the internal name of the class enclosing the instrumented class,
     * or empty string if the instrumented class is not enclosed in another
     * class.
     */
    public String getOuterClassInternalName () {
        Optional<NestHostAttribute> attribute = staticContextData.getClassModel().findAttribute(Attributes.nestHost());
        if (attribute.isEmpty()) {
            return "";
        }
        return attribute.get().nestHost().asInternalName();
    }


    /**
     * Returns the name of the method enclosing the instrumented class, or
     * empty string if the class is not enclosed in a method.
     */
    public String getOuterMethodName () {
        Optional<EnclosingMethodAttribute> attribute = staticContextData.getClassModel().findAttribute(Attributes.enclosingMethod());
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
     * Returns outer method descriptor of the instrumented class. Or empty string if it doesn't
     * have an outer method.
     */
    public String getOuterMethodDescriptor () {
        Optional<EnclosingMethodAttribute> attribute = staticContextData.getClassModel().findAttribute(Attributes.enclosingMethod());
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
     */
    public String getSignature () {
        return Signature.of(staticContextData.getClassModel().thisClass().asSymbol()).signatureString();
    }


    /**
     * Returns the name of the source file containing the instrumented class.
     */
    public String getSourceFile () {
        Optional<SourceFileAttribute> attribute = staticContextData.getClassModel().findAttribute(Attributes.sourceFile());
        if (attribute.isEmpty()) {
            return "";
        }
        return attribute.get().sourceFile().stringValue();
    }


    /**
     * Returns the internal name of the super class of the instrumented class,
     * i.e., a fully qualified class name, with package names delimited by the
     * '/' character.
     */
    public String getSuperClassInternalName () {
        ClassModel thisClass = staticContextData.getClassModel();
        if (thisClass.superclass().isEmpty()) {
            return "";
        } else {
            return thisClass.superclass().get().asInternalName();
        }
    }


    /**
     * Returns class minor version
     */
    public int getMinorVersion () {
        return staticContextData.getClassModel().minorVersion();
    }

    public int getMajorVersion() {
        return staticContextData.getClassModel().majorVersion();
    }

    /**
     * Returns {@code true} if the instrumented class is abstract.
     */
    public boolean isAbstract () {
        return __classAccessFlag (AccessFlag.ABSTRACT);
    }


    /**
     * Returns {@code true} if the instrumented class is an annotation.
     */
    public boolean isAnnotation () {
        return __classAccessFlag (AccessFlag.ANNOTATION);
    }


    /**
     * Returns {@code true} if the instrumented class is an enum.
     */
    public boolean isEnum () {
        return __classAccessFlag (AccessFlag.ENUM);
    }


    /**
     * Returns {@code true} if the instrumented class is final.
     */
    public boolean isFinal () {
        return __classAccessFlag (AccessFlag.FINAL);
    }


    /**
     * Returns {@code true} if the instrumented class is an interface.
     */
    public boolean isInterface () {
        return __classAccessFlag (AccessFlag.INTERFACE);
    }


    /**
     * Returns {@code true} if the instrumented class is private.
     */
    public boolean isPrivate () {
        return __classAccessFlag (AccessFlag.PRIVATE);
    }


    /**
     * Returns {@code true} if the instrumented class is protected.
     */
    public boolean isProtected () {
        return __classAccessFlag (AccessFlag.PUBLIC);
    }


    /**
     * Returns {@code true} if the instrumented class is public.
     */
    public boolean isPublic () {
        return __classAccessFlag (AccessFlag.PUBLIC);
    }


    /**
     * Returns {@code true} if the instrumented class is synthetic.
     */
    public boolean isSynthetic () {
        return __classAccessFlag (AccessFlag.SYNTHETIC);
    }

    //

    private String __classInternalName () {
        return staticContextData.getClassModel().thisClass().asInternalName();
    }


    private boolean __classAccessFlag (final AccessFlag flag) {
        final AccessFlags flags = staticContextData.getClassModel().flags();
        return flags.has(flag);
    }

}
