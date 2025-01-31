package ch.usi.dag.dislre;

/**
 * Provides Shadow VM clients with an interface to register and invoke remote
 * analysis methods.
 * <p>
 * Each remote analysis method should be first registered by a call to the
 * {@link #registerMethod(String)} method, providing a fully qualified method
 * name on the analysis server (without signature).
 * <p>
 * To invoke that method later, either {@link #analysisStart(short)} or
 * {@link #analysisStart(short, byte)} method should be called, followed by
 * calls to appropriate {@code send*} methods. The invocation sequence should
 * end with a call to {@link #analysisEnd()} method.
 */
public final class REDispatch {

    //
    // Analysis method registration.
    //

    /**
     * Registers a remote analysis method. Returns an identifier to be used in
     * subsequent invocations to {@link #analysisStart(short)} and
     * {@link #analysisStart(short, byte) methods.
     *
     * @param fqMethodName
     *        fully qualified name of the method, without signature.
     * @return remote method identifier.
     */
    public static native short registerMethod (String fqMethodName);


    //
    // Analysis method invocation.
    //

    /**
     * Marks the beginning of marshaling phase of the given analysis method
     * invocation.
     *
     * @param methodId
     *        the identifier of the remote analysis method.
     */
    public static native void analysisStart (short methodId);


    /**
     * Marks the beginning of marshaling phase of the given analysis method
     * invocation with the given ordering identifier. Analysis method
     * invocations sharing the same ordering identifier will be mutually
     * (globally) ordered.
     *
     * @param methodId
     *        the identifier of the remote analysis method.
     * @param orderingId
     *        the ordering identifier to associate the analysis method
     *        invocation with. Only non-negative values are valid.
     */
    public static native void analysisStart (short methodId, byte orderingId);


    /**
     * Marks the end of the marshaling phase of analysis method invocation
     * currently ongoing in the caller's thread.
     */
    public static native void analysisEnd ();


    //
    // Primitive types.
    //

    public static native void sendBoolean (boolean value);


    public static native void sendByte (byte value);


    public static native void sendChar (char value);


    public static native void sendShort (short value);


    public static native void sendInt (int value);


    public static native void sendLong (long value);


    public static native void sendFloat (float value);


    public static native void sendDouble (double value);


    //
    // Reference types.
    //

    /**
     * Sends an object reference to the Shadow VM. The analysis method must
     * expect to receive a {@link ch.usi.dag.dislreserver.shadow.ShadowObject}.
     *
     * @param object
     *        the object to send.
     */
    public static native void sendObject (Object object);


    /**
     * Sends an object reference to the Shadow VM, along with data payload
     * containing values provided by special shadow objects such as
     * {@link ch.usi.dag.dislreserver.shadow.ShadowString} and
     * {@link ch.usi.dag.dislreserver.shadow.ShadowThread}.
     *
     * @param object
     *        object to send.
     */
    public static native void sendObjectPlusData (Object object);


    /**
     * Sends the reference to the current thread to the Shadow VM, including
     * {@code null} if the current thread does not exist yet.
     * <p>
     * This is only useful to analyses that run very early during VM
     * initialization. At that time, the call to {@link Thread#currentThread()}
     * may actually return {@code null}, which cannot be sent to the analysis
     * using the {@link #sendObject(Object) sendObject} and
     * {@link #sendObjectPlusData(Object) sendObjectPlusData} methods.
     */
    public static native void sendCurrentThread ();


    //
    // Specials.
    //

    /**
     * Sends object size to the Shadow VM. The analysis method must expect to
     * receive a {@code long} data type.
     *
     * @param object the object the size of which to send.
     */
    public static native void sendObjectSize (Object object);
}
