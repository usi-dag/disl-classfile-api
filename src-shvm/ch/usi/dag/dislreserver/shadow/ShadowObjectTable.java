package ch.usi.dag.dislreserver.shadow;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.Type;

import ch.usi.dag.dislreserver.DiSLREServerFatalException;
import ch.usi.dag.dislreserver.util.Logging;
import ch.usi.dag.util.logging.Logger;


public final class ShadowObjectTable {

    private static final Logger __log = Logging.getPackageInstance ();

    //

    private static final int INITIAL_TABLE_SIZE = 10_000_000;

    private static final ConcurrentHashMap <Long, ShadowObject>
        shadowObjects = new ConcurrentHashMap <> (INITIAL_TABLE_SIZE);

    //

    static void register (final ShadowObject object) {
        if (object == null) {
            __log.warn ("attempting to register a null shadow object");
            return;
        }

        //

        final long objectId = object.getId ();
        final ShadowObject existing = shadowObjects.putIfAbsent (objectId, object);
        if (existing != null && object.isSpecial ()) {
            if (__log.traceIsLoggable ()) {
                __log.trace ("updating shadow object 0x%x", objectId);
            }

            existing.updateFrom (object);
        }
    }


    public static ShadowObject get (final long netReference) {
        final long objectId = NetReferenceHelper.getObjectId (netReference);
        if (objectId == 0) {
            // reserved for null
            return null;
        }

        final ShadowObject existing = shadowObjects.get (objectId);
        if (existing != null) {
            return existing;
        }

        //
        // The corresponding shadow object was not found, so we create it.
        // Only "normal" shadow objects will be generated here, not those
        // representing instances of the Class class.
        //
        if (!NetReferenceHelper.isClassInstance (netReference)) {
            return shadowObjects.computeIfAbsent (objectId, oid -> {
                final ShadowObject result = __createShadowObject (netReference);
                if (__log.traceIsLoggable ()) {
                    __log.trace ("creating %s for 0x%x", result.getClass ().getSimpleName (), oid);
                }

                return result;
            });

        } else {
            throw new DiSLREServerFatalException ("Unknown class instance");
        }
    }


    private static final Type __THREAD_CLASS_TYPE__ = Type.getType (Thread.class);
    private static final Type __STRING_CLASS_TYPE__ = Type.getType (String.class);

    private static ShadowObject __createShadowObject (
        final long netReference
    ) {
        final ShadowClass shadowClass = ShadowClassTable.get (
            NetReferenceHelper.getClassId (netReference)
        );

        if (shadowClass.equalsType (__STRING_CLASS_TYPE__)) {
            return new ShadowString (netReference, shadowClass);

        } else if (shadowClass.extendsType (__THREAD_CLASS_TYPE__)) {
            return new ShadowThread (netReference, shadowClass);

        } else {
            return new ShadowObject (netReference, shadowClass);
        }
    }


    public static void freeShadowObject (final ShadowObject object) {
        shadowObjects.remove (object.getId ());
        ShadowClassTable.freeShadowObject (object);
    }

    //TODO: find a more elegant way to allow users to traverse the shadow object table
    public static Iterator <Entry <Long, ShadowObject>> getIterator () {
        return shadowObjects.entrySet ().iterator ();
    }


    public static Iterable <ShadowObject> objects () {
        return new Iterable <ShadowObject> () {
            @Override
            public Iterator <ShadowObject> iterator () {
                return shadowObjects.values ().iterator ();
            }
        };
    }


    // TODO LB: Make this interface per-shadow-world instead of static.

    public static void registerShadowThread (
        final long netReference, final String name, final boolean isDaemon
    ) {
        final int shadowClassId = NetReferenceHelper.getClassId (netReference);
        final ShadowClass shadowClass = ShadowClassTable.get (shadowClassId);
        final ShadowThread shadowThread = new ShadowThread (
            netReference, shadowClass, name, isDaemon
        );

        register (shadowThread);
    }


    public static void registerShadowString (
        final long netReference, final String value
    ) {
        final int shadowClassId = NetReferenceHelper.getClassId (netReference);
        final ShadowClass shadowClass = ShadowClassTable.get (shadowClassId);
        final ShadowString shadowString = new ShadowString (
            netReference, shadowClass, value
        );

        register (shadowString);
    }

}
