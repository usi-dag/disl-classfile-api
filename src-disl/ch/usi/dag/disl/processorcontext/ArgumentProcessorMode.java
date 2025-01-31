package ch.usi.dag.disl.processorcontext;

/**
 * Determines the scope of {@link ArgumentProcessorContext} methods.
 */
public enum ArgumentProcessorMode {

	/**
	 * Arguments of the current method.
	 */
	METHOD_ARGS,

	/**
	 * Arguments of the method being invoked.
	 */
	CALLSITE_ARGS
}
