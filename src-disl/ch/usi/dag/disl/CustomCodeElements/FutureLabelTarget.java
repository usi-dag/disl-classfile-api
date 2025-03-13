package ch.usi.dag.disl.CustomCodeElements;

import java.lang.classfile.Attributes;
import java.lang.classfile.CustomAttribute;

// TODO what to put at the place of T in CustomAttribute<T>  ??????
//  since like this I am using a raw parametrized class
public class FutureLabelTarget extends CustomAttribute {

    // this class represent a LabelTarget that will be added in the future
    // since we cannot create LabelTarget or Label without a code Builder

    // TODO does this class need other informations????

    public FutureLabelTarget() {
        // TODO what to pass here????
        super(Attributes.code());
    }


}
