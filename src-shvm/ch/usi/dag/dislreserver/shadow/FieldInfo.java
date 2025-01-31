package ch.usi.dag.dislreserver.shadow;

import java.lang.reflect.Modifier;

import org.objectweb.asm.tree.FieldNode;


public final class FieldInfo {

    private final FieldNode __fieldNode;

    //

    FieldInfo (final FieldNode fieldNode) {
        __fieldNode = fieldNode;
    }

    //

    public String getName () {
        return __fieldNode.name;
    }


    public int getModifiers () {
        return __fieldNode.access;
    }


    public String getDescriptor () {
        return __fieldNode.desc;
    }

    //

    public boolean isPublic () {
        return Modifier.isPublic (getModifiers ());
    }

}
