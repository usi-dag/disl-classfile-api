package ch.usi.dag.dislreserver.shadow;

final class NetReferenceHelper {

    /**
     * 40-bit object (instance) identifier starting at bit 0.
     */
    private static final short OBJECT_ID_SHIFT = 0;
    private static final short OBJECT_ID_BITS = 40;
    private static final long OBJECT_ID_MASK = __mask (OBJECT_ID_BITS, OBJECT_ID_SHIFT);

    /**
     * 22-bit class identifier starting at bit 40.
     */
    private static final short CLASS_ID_SHIFT = OBJECT_ID_SHIFT + OBJECT_ID_BITS;
    private static final short CLASS_ID_BITS = 22;
    private static final long CLASS_ID_MASK = __mask (CLASS_ID_BITS, CLASS_ID_SHIFT);

    /**
     * 1-bit class instance flag at bit 62.
     */
    private static final short CLASS_FLAG_SHIFT = CLASS_ID_SHIFT + CLASS_ID_BITS;
    private static final short CLASS_FLAG_BITS = 1;
    private static final long CLASS_FLAG_MASK = __mask (CLASS_FLAG_BITS, CLASS_FLAG_SHIFT);

    /**
     * 1-bit special payload flag at bit 63. Indicates that the object has
     * extra payload attached.
     */
    private static final short SPECIAL_FLAG_SHIFT = CLASS_FLAG_SHIFT + CLASS_FLAG_BITS;
    private static final short SPECIAL_FLAG_BITS = 1;
    private static final long SPECIAL_FLAG_MASK = __mask (SPECIAL_FLAG_BITS, SPECIAL_FLAG_SHIFT);

    //

    static long getUniqueId (final long netReference) {
        // The mask used here needs to have an absolute position.
        return netReference & ~SPECIAL_FLAG_MASK;
    }


    static long getObjectId (final long netReference) {
        return __bits (netReference, OBJECT_ID_MASK, OBJECT_ID_SHIFT);
    }


    static int getClassId (final long netReference) {
        return (int) __bits (netReference, CLASS_ID_MASK, CLASS_ID_SHIFT);
    }


    static boolean isClassInstance (final long netReference) {
        return __flag (netReference, CLASS_FLAG_MASK);
    }


    static boolean isSpecial (final long netReference) {
        return __flag (netReference, SPECIAL_FLAG_MASK);
    }

    //

    private static boolean __flag (final long netReference, final long mask) {
        return (netReference & mask) != 0;
    }


    /**
     * Returns bits from the given {@code long} value masked using the given
     * mask and shifted to the right by the given amount.
     */
    static long __bits (
        final long value, final long mask, final short shift
    ) {
        return (value & mask) >>> shift;
    }


    static long __mask (final int length, final int shift) {
        assert length > 0 && shift >= 0;
        return ((1L << length) - 1) << shift;
    }

}
