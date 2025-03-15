package ch.usi.dag.disl.localvar;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;


public abstract class AbstractLocalVar {

    private final static String NAME_DELIM = ".";

    //

    private final String className;

    private final String fieldName;

    private final ClassDesc typeDesc;


    public AbstractLocalVar(final String className, final String fieldName, final ClassDesc typeDesc) {
        this.className = className;
        this.fieldName = fieldName;
        this.typeDesc = typeDesc;
    }


    public String getID () {
        return fqFieldNameFor (className, fieldName);
    }


    public String getOwner () {
        return className;
    }


    public String getName () {
        return fieldName;
    }


    public ClassDesc getType() {return typeDesc;}

    public TypeKind getTypeKind() {
        return TypeKind.fromDescriptor(typeDesc.descriptorString());
    }

    public String getDescriptor () {
        return typeDesc.descriptorString();
    }

    //

    /**
     * Returns a fully qualified internal field name for the given class name
     * and field name.
     *
     * @param ownerClassName
     *      internal name of the field owner class
     * @param fieldName
     *      name of the field within the class
     *
     * @return
     *      Fully qualified field name.
     */
    public static String fqFieldNameFor (
        final String ownerClassName, final String fieldName
    ) {
        return ownerClassName + NAME_DELIM + fieldName;
    }
}
