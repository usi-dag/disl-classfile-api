package ch.usi.dag.disl.test.suite;

import java.io.IOException;

import ch.usi.dag.disl.test.utils.ClientEvaluationRunner;
import ch.usi.dag.disl.test.utils.Runner;


public abstract class ShadowVmOnlyTest extends BaseTest {

    @Override
    protected Runner _createRunner () {
        return new ClientEvaluationRunner (this.getClass ());
    }


    @Override
    protected final void _checkOutErr (final Runner runner) throws IOException {
        _checkOutErr ((ClientEvaluationRunner) runner);
    }


    protected void _checkOutErr (
        final ClientEvaluationRunner runner
    ) throws IOException {
        runner.assertShadowOut ("evaluation.out.resource");
    }

}
