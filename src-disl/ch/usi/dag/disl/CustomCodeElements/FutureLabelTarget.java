package ch.usi.dag.disl.CustomCodeElements;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.Label;

public class FutureLabelTarget extends CustomAttribute<FutureLabelTarget> {

    // this class represent a LabelTarget that will be added in the future
    // since we cannot create LabelTarget or Label without a code Builder.
    // The idea is that before adding this element to the codeBuilder we
    // replace it with an actual LabelTarget, if we do have a Label stored in this
    // class we can use it. Otherwise, we can just create a new Label
    private Label label;

    public FutureLabelTarget() {
        super(new FutureLabelTargetMapper());
        label = null;
    }

    public FutureLabelTarget(Label label) {
        super(new FutureLabelTargetMapper());
        this.label = label;
    }

    public boolean hasLabel() {
        return this.label != null;
    }

    public Label getLabel() {
        return this.label;
    }

    public void setLabel(Label label) {
        this.label = label;
    }

}
