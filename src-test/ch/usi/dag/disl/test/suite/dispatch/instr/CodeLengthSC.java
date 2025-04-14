package ch.usi.dag.disl.test.suite.dispatch.instr;

import ch.usi.dag.disl.util.ClassFileHelper;

import ch.usi.dag.disl.staticcontext.AbstractStaticContext;

import java.lang.classfile.CodeElement;
import java.util.List;


public class CodeLengthSC extends AbstractStaticContext {

    public int methodSize() {
        return staticContextData.getMethodModel().instructions().size();
    }

    public int codeSize() {
        CodeElement ain = staticContextData.getRegionStart();

        int size = 0;

        // count the size until the first end
        while(ain != null && ain != staticContextData.getRegionEnds().getFirst()) {
            ++size;
            List<CodeElement> instr = staticContextData.getMethodModel().instructions();
            ain = ClassFileHelper.nextInstruction(instr, ain);
        }

        if(ain == null) {
            size = 0;
        }

        return size;
    }

}
