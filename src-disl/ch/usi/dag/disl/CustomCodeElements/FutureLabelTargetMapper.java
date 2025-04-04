package ch.usi.dag.disl.CustomCodeElements;

import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassReader;

public class FutureLabelTargetMapper implements AttributeMapper<FutureLabelTarget> {

    @Override
    public String name() {
        return "FutureLabelTargetMapper";
    }

    @Override
    public FutureLabelTarget readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
        return null;
    }

    @Override
    public void writeAttribute(BufWriter buf, FutureLabelTarget attr) {

    }

    @Override
    public AttributeStability stability() {
        return null;
    }

    public FutureLabelTargetMapper() {

    }
}
