package ch.usi.dag.disl.localvar;

import ch.usi.dag.disl.annotation.SyntheticLocal;

import java.lang.classfile.CodeElement;
import java.lang.constant.ClassDesc;
import java.util.List;


public class SyntheticLocalVar extends AbstractLocalVar {

    private final SyntheticLocal.Initialize initialize;

    private List<CodeElement> initCodeList;


    public SyntheticLocalVar(
            String className, String fieldName, final ClassDesc typeDesc, SyntheticLocal.Initialize initialize
            ) {
        super(className, fieldName, typeDesc);
        this.initialize = initialize;
    }


    public SyntheticLocal.Initialize getInitialize () {
        return initialize;
    }


    public List<CodeElement> getInitCodeList() {return initCodeList;}

    public boolean hasInitCode () {
        return initCodeList != null;
    }

    public void setInitCodeList(List<CodeElement> initCodeList) {
        this.initCodeList = initCodeList;
    }
}
