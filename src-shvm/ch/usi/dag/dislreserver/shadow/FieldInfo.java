package ch.usi.dag.dislreserver.shadow;

import java.lang.classfile.FieldModel;
import java.lang.reflect.Modifier;

public final class FieldInfo {

    private final FieldModel __fieldNode;

    //

    FieldInfo (final FieldModel fieldNode) {
        __fieldNode = fieldNode;
    }

    //

    public String getName () {
        return __fieldNode.fieldName().stringValue();
    }


    public int getModifiers () {
        return __fieldNode.flags().flagsMask();
    }


    public String getDescriptor () {
        return __fieldNode.fieldTypeSymbol().descriptorString();
    }

    //

    public boolean isPublic () {
        return Modifier.isPublic (getModifiers ());
    }

}
