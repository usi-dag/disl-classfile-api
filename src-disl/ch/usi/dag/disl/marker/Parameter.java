package ch.usi.dag.disl.marker;

/**
 * Used for marker parameter parsing.
 */
public final class Parameter {

    protected String __value;

    /**
     * Create parameter with a value.
     */
    public Parameter(final String value) {
        __value = value;
    }


    /**
     * Get parameter value.
     */
    public String getValue() {
        return __value;
    }


    /**
     * Retrieves multiple values by splitting the annotation parameter using the
     * given delimiter.
     *
     * @param delimiter
     *        the delimiter to use for splitting the parameter
     * @return An array of values obtained by splitting the annotation
     *         parameter.
     */
    public String [] getMultipleValues (final String delimiter) {
        return __value.split (delimiter);
    }
}
