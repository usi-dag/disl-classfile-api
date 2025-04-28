package ch.usi.dag.disl.scope;

import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import ch.usi.dag.disl.util.JavaNames;

abstract class TypeMatcher {

    public abstract boolean match (final String typeDesc);
    public abstract String pattern ();

    @Override
    public String toString () {
        return String.format ("%s[%s]", JavaNames.simpleClassName (this), pattern ());
    }

    //

    private static final TypeMatcher __matchAny__ = new TypeMatcher () {
        @Override
        public boolean match (final String typeDesc) {
            return true;
        };

        @Override
        public String pattern () {
            return "<any>";
        };
    };

    private static final TypeMatcher __matchNone__ = new TypeMatcher () {
        @Override
        public boolean match (final String typeDesc) {
            return false;
        };

        @Override
        public String pattern() {
            return "<none>";
        };
    };

    //

    private static final class Generic extends TypeMatcher {
        private final WildCardMatcher __matcher;

        private Generic (final WildCardMatcher matcher) {
            __matcher = matcher;
        }

        //

        @Override
        public boolean match (final String typeDesc) {
            //
            // Object type descriptors without package specification need
            // special handling. To match patterns with package wild card,
            // we need to add package separator to such descriptors so that
            // the default package is an empty package.
            //
            if (__isObjectType (typeDesc) && !JavaNames.internalNameHasPackage (typeDesc)) {
                return __matcher.match (__fixupDefaultPackage (typeDesc));
            } else {
                return __matcher.match (typeDesc);
            }
        }

        private static final char __OBJECT_TYPE_CHAR__ = 'L';

        private boolean __isObjectType (final String descriptor) {
            return !descriptor.isEmpty() && descriptor.charAt (0) == __OBJECT_TYPE_CHAR__;
        }

        private static final char __PKG_SEPARATOR_CHAR__ = '/';

        private static String __fixupDefaultPackage (final String descriptor) {
            return String.valueOf(descriptor.charAt(0)) +
                    __PKG_SEPARATOR_CHAR__ +
                    descriptor.substring(1);
        }

        //

        @Override
        public String pattern() {
            return __matcher.pattern ();
        }
    }

    //

    static TypeMatcher forPattern (final String typePattern) {
        final String trimmed = Objects.requireNonNull (typePattern).trim ();

        //

        if (trimmed.isEmpty ()) {
            return __matchNone__;

        } else if (trimmed.equals (WildCardMatcher.WILDCARD)) {
            return __matchAny__;

        } else {
            final String descPattern = __getDescriptorPattern (trimmed);
            final WildCardMatcher matcher = WildCardMatcher.forPattern (
                    JavaNames.typeToInternal (descPattern)
            );

            return new Generic (matcher);
        }
    }

    //

    private static final String __ARRAY_BRACKETS__ = "[]";
    private static final int __ARRAY_BRACKETS_LENGTH__ = __ARRAY_BRACKETS__.length ();

    private static String __getDescriptorPattern (final String input) {
        final int firstPosition = input.indexOf (__ARRAY_BRACKETS__);
        if (firstPosition < 0) {
            return __getTypeDescriptor (input);

        } else {
            final StringBuilder result = new StringBuilder ();

            final int dimensions = 1 + __getDimensionCount (input, firstPosition + __ARRAY_BRACKETS_LENGTH__);
            result.append ("[".repeat(Math.max(0, dimensions)));

            final String typeName = input.substring (0, firstPosition);
            result.append (__getTypeDescriptor (typeName));

            return result.toString ();
        }
    }

    private static int __getDimensionCount (final String input, final int startIndex) {
        int result = 0;
        final int charCount = input.length ();

        int fromIndex = startIndex;
        LOOP: while (fromIndex < charCount) {
            final int position = input.indexOf (__ARRAY_BRACKETS__, fromIndex);
            if (position < 0) {
                break LOOP;
            }

            fromIndex += __ARRAY_BRACKETS_LENGTH__;
            result++;
        }

        return result;
    }


    private static final Map <String, TypeKind> __PRIMITIVES__ = new HashMap<>() {{
        put("void", TypeKind.VOID);
        put("boolean", TypeKind.BOOLEAN);
        put("byte", TypeKind.BYTE);
        put("char", TypeKind.CHAR);
        put("short", TypeKind.SHORT);
        put("int", TypeKind.INT);
        put("float", TypeKind.FLOAT);
        put("long", TypeKind.LONG);
        put("double", TypeKind.DOUBLE);
    }};

    private static String __getTypeDescriptor (final String input) {
        //
        // If the descriptor is a wild card, match any type, including
        // primitive types.
        //
        if (WildCardMatcher.WILDCARD.equals (input)) {
            return input;
        }

        //

        final TypeKind primitiveType = __PRIMITIVES__.get (input);
        if (primitiveType == null) {
            final String fqcn = __getClassName (input);
            try {
                ClassDesc obj = ClassDesc.ofInternalName(fqcn);
                return obj.descriptorString();
            } catch (Exception e) {
                // this might not seem correct, but otherwise the original tests do not pass.
                // this was done to preserve the original behaviour
                return "L" + fqcn + ";";
            }
        } else {
            return primitiveType.upperBound().descriptorString();
        }
    }

    private static final String __DEFAULT_PKG_WITH_SEPARATOR__ = "[default].";

    private static String __getClassName (final String input) {
        if (input.startsWith (WildCardMatcher.WILDCARD)) {
            // This will already match any prefix, no modifications needed.
            return input;

        } else if (!JavaNames.typeNameHasPackage (input)) {
            // Append package wild card to classes without package specification.
            return JavaNames.typeNameJoin (WildCardMatcher.WILDCARD, input);

        } else if (input.startsWith (__DEFAULT_PKG_WITH_SEPARATOR__)) {
            // Strip the [default] package specifier, leaving the separator.
            return input.substring (__DEFAULT_PKG_WITH_SEPARATOR__.length () - 1);

        } else {
            return input;
        }
    }

}
