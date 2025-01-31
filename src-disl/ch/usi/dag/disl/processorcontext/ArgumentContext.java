package ch.usi.dag.disl.processorcontext;

import ch.usi.dag.disl.annotation.ArgumentProcessor;

/**
 * Provides information about a particular method argument from within an
 * {@link ArgumentProcessor} snippet.
 */
public interface ArgumentContext {

    /**
     * @return the position of the argument currently being processed.
     */
    int getPosition ();


    /**
     * @return the type descriptor of the processed argument.
     */
    String getTypeDescriptor ();


    /**
     * @return the total number of arguments to be processed.
     */
    int getTotalCount ();
}
