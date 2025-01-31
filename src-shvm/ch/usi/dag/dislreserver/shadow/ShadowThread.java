package ch.usi.dag.dislreserver.shadow;

import java.util.Formattable;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicReference;


// TODO Make it clear that extra data have not yet been sent over the network.
//
// Consider returning an Optional (if we can distinguish that the data has
// not been set) or keep the data in a separate info class that can be swapped
// atomically.
//
public final class ShadowThread extends ShadowObject implements Formattable {

    /**
     * Data class for holding the additional information about a thread. Keeping
     * the data in one class allows updating the thread information atomically.
     */
    private static final class Info {
        final String name;
        final boolean isDaemon;

        Info (final String name, final boolean isDaemon) {
            this.name = name;
            this.isDaemon = isDaemon;
        }
    }

    //

    private final AtomicReference <Info> __info;

    //

    ShadowThread (final long netReference, final ShadowClass shadowClass) {
        super (netReference, shadowClass);
        __info = new AtomicReference <> ();
    }

    ShadowThread (
        final long netReference, final ShadowClass shadowClass,
        final String name, final boolean isDaemon
    ) {
        super (netReference, shadowClass);
        __info = new AtomicReference <> (new Info (name, isDaemon));
    }


    // TODO warn user that it will return null when the ShadowThread is not yet sent.
    // TODO LB: Consider switching to Optional
    public String getName () {
        final Info info = __info.get ();
        return (info != null) ? info.name : null;
    }


    // TODO warn user that it will return false when the ShadowThread is not yet sent.
    // TODO LB: Switch to using Optional, or a Boolean reference
    public boolean isDaemon () {
        final Info info = __info.get ();
        return (info != null) ? info.isDaemon : false;
    }

    //

    @Override
    protected void _updateFrom (final ShadowObject object) {
        //
        // Update the thread information from the other shadow thread.
        // Both objects are expected to have the same net reference.
        //
        if (object instanceof ShadowThread) {
            final ShadowThread other = (ShadowThread) object;
            final Info otherInfo = other.__info.get ();
            if (otherInfo != null) {
                this.__info.updateAndGet (v -> otherInfo);
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

        final Info info = __info.get ();
        formatter.format (" <%s>", (info != null) ? info.name : "unknown");
    }

}
