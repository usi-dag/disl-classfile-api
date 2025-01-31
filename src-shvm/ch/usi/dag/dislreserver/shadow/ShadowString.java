package ch.usi.dag.dislreserver.shadow;

import java.util.Formatter;
import java.util.concurrent.atomic.AtomicReference;


// TODO ShadowString should better handle if String data are not send
// over network - throw a runtime exception ??
public final class ShadowString extends ShadowObject {

    private final AtomicReference <String> __value;

    //

    ShadowString (
        final long netReference, final ShadowClass shadowClass
    ) {
        this (netReference, shadowClass, null);
    }

    ShadowString (
        final long netReference, final ShadowClass shadowClass,
        final String value
    ) {
        super (netReference, shadowClass);
        __value = new AtomicReference <> (value);
    }

    //

    // TODO warn user that it will return null when the ShadowString is not yet sent.
    @Override
    public String toString () {
        return __value.get ();
    }

    //

    @Override
    protected void _updateFrom (final ShadowObject object) {
        //
        // If the value of this string has not yet been initialized,
        // update it from the other shadow string. The other string
        // is expected to have the same net reference.
        //
        if (__value.get () == null) {
            if (object instanceof ShadowString) {
                final ShadowString other = (ShadowString) object;
                __value.updateAndGet (v -> other.__value.get ());
            }
        }
    }

    //

    @Override
    public void formatTo (
        final Formatter formatter,
        final int flags, final int width, final int precision
    ) {
        super.formatTo (formatter, flags, width, precision);

        final String value = __value.get ();
        if (value != null) {
            formatter.format (" <%s>", value);
        }
    }

}
