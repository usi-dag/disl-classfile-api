package ch.usi.dag.disl.test.suite.guard.instr;

import ch.usi.dag.disl.staticcontext.AbstractStaticContext;


public class CodeSC extends AbstractStaticContext {

    public int codeLength () {
        return staticContextData.getMethodModel().instructions().size();
    }

}
