package ch.usi.dag.disl.processorcontext;

/**
 * Provides access to method arguments. This can be done either by querying the
 * arguments directly using the {@link #getArgs(ArgumentProcessorMode) getArgs)
 * method, which returns a newly allocated array containing the method
 * arguments, or by applying an argument processor using the
 * {@link #apply(Class, ArgumentProcessorMode) apply} method, which allows
 * iterating over the arguments without having to allocate any memory.
 * <p>
 * Depending on the {@link ArgumentProcessorMode}, the methods of an
 * {@link ArgumentProcessorContext} refer either to the currently executing
 * method, or to the current call site.
 */
public interface ArgumentProcessorContext {

    /**
     * Applies the given argument processor to method arguments, either at call-site
     * or within an invoked method.
     *
     * @param argumentProcessorClass
     *        argument processor class to apply
     * @param mode
     *        where to apply the argument processor.
     */
    void apply (Class <?> argumentProcessorClass, ArgumentProcessorMode mode);


    /**
     * Returns the receiver of the method invocation or {@code null} for static
     * methods.
     *
     * @param mode
     *        for which should be the object retrieved
     */
    Object getReceiver (ArgumentProcessorMode mode);


    /**
     * Returns an object array containing the method arguments. Note that
     * primitive types will be boxed.
     *
     * @param mode
     *        for which should be the argument array retrieved
     */
    Object [] getArgs (ArgumentProcessorMode mode);
}
