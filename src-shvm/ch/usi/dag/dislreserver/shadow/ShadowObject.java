package ch.usi.dag.dislreserver.shadow;

import java.util.Formattable;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import ch.usi.dag.dislreserver.DiSLREServerFatalException;


public class ShadowObject implements Formattable {

    private final long __netReference;

    /**
     * The {@link ShadowClass} of this object. If {@code null}, then this object
     * represents an instance of the {@link Class} class, or (in a singular
     * case) the bootstrap class loader.
     */
    private final ShadowClass __shadowClass;

    private final AtomicReference <Object> __shadowState;

    //

    ShadowObject (final long netReference, final ShadowClass shadowClass) {
        __netReference = netReference;
        __shadowClass = shadowClass;
        __shadowState = new AtomicReference <> ();
    }

    //
    // These methods are meant to be only used internally, so that we avoid
    // exposing the net reference.
    //

    final long getUniqueId () {
        return NetReferenceHelper.getUniqueId (__netReference);
    }


    final int getClassId () {
        return NetReferenceHelper.getClassId (__netReference);
    }


    final boolean isClassInstance () {
        return NetReferenceHelper.isClassInstance (__netReference);
    }


    final boolean isSpecial () {
        return NetReferenceHelper.isSpecial (__netReference);
    }

    //

    public final long getId () {
        return NetReferenceHelper.getObjectId (__netReference);
    }


    public ShadowClass getShadowClass () {
        if (__shadowClass != null) {
            return __shadowClass;

        } else {
            //
            // FIXME LB: Consider not exposing the BOOTSTRAP_CLASSLOADER reference to the user.
            //
            // Then there should be no need for this hackery.
            //
            if (this.equals (ShadowClassTable.BOOTSTRAP_CLASSLOADER)) {
                throw new NullPointerException ();
            }

            return ShadowClassTable.JAVA_LANG_CLASS.get ();
        }
    }

    //

    public final Object getState () {
        return __getState (Object.class);
    }


    public final <T> T getState (final Class <T> type) {
        return __getState (type);
    }


    private final <T> T __getState (final Class <T> type) {
        return type.cast (__shadowState.get ());
    }


    public final void setState (final Object state) {
        __shadowState.set (state);
    }


    public final Object setStateIfAbsent (final Object state) {
        return computeStateIfAbsent (Object.class, () -> state);
    }


    public final <T> T setStateIfAbsent (final Class <T> type, final T state) {
        return computeStateIfAbsent (type, () -> state);
    }


    public final Object computeStateIfAbsent (final Supplier <Object> supplier) {
        return computeStateIfAbsent (Object.class, supplier);
    }


    public final <T> T computeStateIfAbsent (
        final Class <T> type, final Supplier <T> supplier
    ) {
        //
        // Avoid CAS if state is already present.
        // Otherwise compute new state and try to CAS the new state once.
        // If that fails, return whatever was there.
        //
        final T existing = __getState (type);
        if (existing != null) {
            return existing;
        }

        final T supplied = supplier.get ();
        if (__shadowState.compareAndSet (null, supplied)) {
            return supplied;
        }

        return __getState (type);
    }


    //

    @Override
    public int hashCode () {
        //
        // The part of net reference that identifies object instances is global and
        // 40 bits long, so just using the lower 32 bits of the object identifier
        // should be fine.
        //
        // If we were numbering objects per class, we would also need to take the
        // class identifier into account to get a better hash code.
        //
        return (int) getId ();
    }


    @Override
    public boolean equals (final Object object) {
        if (this == object) {
            return true;
        }

        if (object instanceof ShadowObject) {
            return __equals ((ShadowObject) object);
        }

        return false;
    }


    private boolean __equals (final ShadowObject other) {
        //
        // The equality of shadow objects should be based purely on the equality of
        // the net reference, without taking into account the special bit which
        // indicates whether the object has been sent with additional data.
        //
        // The value of some special shadow objects can be updated lazily, so we
        // should not really compare their values unless we can make sure that
        // the comparison makes sense at all times.
        //
        return this.getUniqueId () == other.getUniqueId ();
    }

    //

    final void updateFrom (final ShadowObject other) {
        //
        // When updating value from another shadow object, the net reference
        // of this and the other object (without the special bit) must be the same.
        // The other object must have the SPECIAL bit set (this is checked by the
        // caller of this method).
        //
        if (this.__equals (other)) {
            _updateFrom (other);

        } else {
            throw new DiSLREServerFatalException (String.format (
                "attempting to update object 0x%x using object 0x%x",
                __netReference, other.__netReference
            ));
        }
    }


    /**
     * This method is intended to be overriden by subclasses to update shadow
     * object's internal values. The caller of this method guarantees that the
     * unique id part of the net reference of the object to update from will be
     * the same as that of this shadow object. In addition, the other object
     * will have the special bit of the net reference set, indicating that it
     * contains additional payload specific to that object.
     *
     * @param object
     *        the {@link ShadowObject} instance to update from.
     */
    protected void _updateFrom (final ShadowObject object) {
        // By default do nothing.
    }

    //

    @Override
    public void formatTo (
        final Formatter formatter,
        final int flags, final int width, final int precision
    ) {
        formatter.format (
            "%s@%x",
            (__shadowClass != null) ? __shadowClass.getName () : "<missing>",
            getId ()
        );
    }

}
