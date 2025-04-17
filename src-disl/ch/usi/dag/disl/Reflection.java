package ch.usi.dag.disl;

import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.usi.dag.disl.util.JavaNames;

import static java.lang.constant.ConstantDescs.*;


/**
 * Maintains reflective information about instrumented classes.
 */
public final class Reflection {

    private static final ClassLoader __root__ = new SystemClassLoader ();

    //

    public static ClassLoader systemClassLoader() {
        return __root__;
    }

    //

    public static class MissingClassException extends RuntimeException {
        final String __internalName;

        MissingClassException (final String format, final String internalName) {
            super (String.format (format,  internalName));
            __internalName = internalName;
        }

        public String classInternalName () {
            return __internalName;
        }
    }

    //

    public static abstract class ClassLoader {
        /**
         * Maps type names to class instances. For reference types, the type
         * name is the internal class name. For primitives types, the type name
         * is the corresponding keyword. Array types have an appropriate number
         * of square bracket pairs attached to the type name.
         * <p>
         * Examples:
         * <ul>
         * <li>void
         * <li>int
         * <li>int[]
         * <li>java/lang/Object
         * <li>java/lang/Object[]
         * </ul>
         */
        private final ConcurrentMap <ClassDesc, Class> __classes = new ConcurrentHashMap <> ();

        void notifyClassLoaded (final ClassModel cm) {
            __classes.computeIfAbsent(
                    cm.thisClass().asSymbol(), k -> new RegularClass(this, cm)
            );
        }

        //

        public abstract Optional <ClassLoader> parent ();

        //

        /**
         * Looks up a {@link Class} for the given internal class name, i.e., a
         * fully qualified class name, with package elements separated by the
         * '/' character.
         * <p>
         * <b>Note:</b>This method is only supposed to be used for reference
         * types. Behavior with primitive or array types is undefined. If
         * necessary, use the { #classForType(Type)} method instead.
         *
         * @param internalName
         *        the internal name of the class to look up.
         * @return an {@link Optional} containing the {@link Class} corresponding
         *         to the given class name, or an empty {@link Optional} if the
         *         class could not be found.
         */
        public final Optional <Class> classForInternalName (final String internalName) {
            return Optional.ofNullable (_lookupName (internalName));
        }

        public final Optional<Class> classForType(final ClassDesc desc) {
            return Optional.ofNullable(_lookupType(desc));
        }

        /**
         * Looks up a reference type using its internal name and returns the
         * corresponding {@link Class} if the type is known to the class loader.
         * <p>
         * This method is intended to be overridden to allow the lookup to be
         * modified by different class loader implementations.
         *
         * @param internalName
         *        the internal name of the class to look up
         * @return the {@link Class} corresponding to the given type, or
         *         {@code null} if the type is not known.
         */
        protected Class _lookupName (final String internalName) {
            return __classes.get (ClassDesc.ofInternalName(internalName));
        }

        /**
         * Looks up the given type and returns the corresponding {@link Class}
         * if the type is known to the class loader.
         * <p>
         * This method is intended to be overridden to allow the lookup to be
         * modified by different class loader implementations. The default
         * implementation only looks up reference types.
         *
         * @return the {@link Class} corresponding to the given type, or
         *         {@code null} if the type is not known.
         */
        protected Class _lookupType(final ClassDesc desc) {
            return (desc.isClassOrInterface()) ?
                    __classes.get(desc) : null;
        }

    }


    static class RegularClassLoader extends ClassLoader {
        /**
         * The parent class loader.
         */
        private final Optional <ClassLoader> __parent;

        //

        private RegularClassLoader (final ClassLoader parent) {
            __parent = Optional.of (parent);
        }

        //

        @Override
        public Optional <ClassLoader> parent () {
            return __parent;
        }
    }


    static final class SystemClassLoader extends ClassLoader {

        private final ConcurrentMap <ClassDesc, Class> __primitives =
            Stream.of(
                CD_void, CD_boolean, CD_byte, CD_char, CD_short, CD_int, CD_float, CD_long, CD_double
            ).collect (Collectors.toConcurrentMap (
                Function.identity (), PrimitiveClass::new
            ));

        private final ConcurrentMap <ClassDesc, Class> __arrays = new ConcurrentHashMap <> ();


        //

        @Override
        public Optional <ClassLoader> parent () {
            return Optional.empty ();
        }

        @Override
        protected Class _lookupType (final ClassDesc type) {
            if (type.isClassOrInterface()) {
                return super._lookupType (type);

            } else if (type.isArray()) {
                return __arrays.computeIfAbsent (
                    type, ArrayClass::new
                );

            } else {
                return __primitives.get (type);
            }
        }

    }

    //

    public interface Class {
        /**
         * @return the class loader of this class.
         */
        ClassLoader classLoader ();


        /**
         * Returns the type name of this class.
         * <p>
         * For reference types, this means the fully qualified name of the
         * class, with package elements separated by the '.' character. A type
         * name retains the '$' character as the separator between the other and
         * the inner class names.
         * <p>
         * For primitive types, this means the keyword representing the type.
         * <p>
         * For array types, this means the array component type as above,
         * followed by a corresponding number of pair square brackets.
         *
         * @return the canonical name of this class.
         */
        String typeName ();


        /**
         * Returns the internal name of this class.
         * <p>
         * For reference types, this means the fully qualified class name with
         * package elements separated by the '/' character.
         * <p>
         * Primitive types do not really have an internal name, but for
         * simplicity, we just use the corresponding keyword. This differs from
         * {Type} in ASM, which does not provide internal name for
         * primitive types.
         *
         * @return the internal name of this class.
         */
        String internalName ();


        /**
         * @return {@code true} if this class represents a primitive type.
         */
        boolean isPrimitive();


        /**
         * Returns {@code true} if this class is an array.
         */
        boolean isArray ();


        /**
         * Returns {@code true} if this class is an interface.
         */
        boolean isInterface ();


        /**
         * @return an {@link Optional} super class of this class, or an empty
         *         {@link Optional} if the class does not have a super
         *         class (this only holds for the {@link Object} class).
         * @throws MissingClassException
         *         if the class could not be found.
         */
        Optional <Class> superClass ();


        /**
         * @return a {@link Stream} of interfaces implemented by this class.
         * @throws MissingClassException
         *         if any of the interfaces could not be found.
         */
        Stream <Class> interfaces ();


        /**
         * @return a {@link Stream} of interface types implemented by this
         *         class.
         * @throws MissingClassException
         *         if any of the interfaces could not be found.
         */
        Stream <ClassDesc> interfaceTypes ();


        /**
         * Returns an {@link Optional} containing the {@link Method}
         * corresponding to the given signature. The {@link Optional} will be
         * empty if they could not be found.
         *
         * @param sig
         *        the method signature to look for
         * @return an {@link Optional} containing the {@link Method}
         *         corresponding to the given signature.
         */
        Optional <Method> methodForSignature (final String sig);
    }

    //

    static final class PrimitiveClass implements Class {

        private final ClassDesc __type;

        private PrimitiveClass (final ClassDesc type) {
            __type = type;
        }

        @Override
        public ClassLoader classLoader () {
            return __root__;
        }

        @Override
        public String typeName () {
            return __primitiveTypeKeyword ();
        }

        @Override
        public String internalName () {
            return __primitiveTypeKeyword ();
        }

        private String __primitiveTypeKeyword () {
            return __type.displayName();
        }

        @Override
        public boolean isPrimitive () {
            return true;
        }

        @Override
        public boolean isArray () {
            return false;
        }

        @Override
        public boolean isInterface () {
            return false;
        }

        @Override
        public Optional <Class> superClass () {
            return Optional.empty ();
        }

        @Override
        public Stream <Class> interfaces () {
            return Stream.empty ();
        }

        @Override
        public Stream <ClassDesc> interfaceTypes () {
            return Stream.empty ();
        }

        @Override
        public Optional <Method> methodForSignature (final String sig) {
            return Optional.empty ();
        }

    }

    //

    static class RegularClass implements Class {
        protected final String __internalName;
        private final ClassLoader __loader;
        private final int __modifiers;
        private final Optional<ClassEntry> __superName;
        private final Map <String, Method> __methods;
        private final List<ClassEntry> __interfaces;

        private RegularClass(final ClassLoader loader, final ClassModel classModel) {
            __loader = loader;
            __internalName = classModel.thisClass().asInternalName();
            __modifiers = classModel.flags().flagsMask();
            __superName = classModel.superclass();

            List<MethodModel> methodsList = classModel.elementList()
                    .stream()
                    .filter(e -> e instanceof MethodModel)
                    .map(e -> (MethodModel) e)
                    .collect(Collectors.toList());
            __methods = __createMethods(methodsList);

            __interfaces = classModel.interfaces();

        }

        private  ConcurrentMap<String, Method> __createMethods(final List<MethodModel> methods) {
            return methods.parallelStream()
                    .map(methodModel -> new Method(this, methodModel))
                    .collect(Collectors.toConcurrentMap(
                            Method::signature, Function.identity()
                    ));
        }

        @Override
        public ClassLoader classLoader () {
            return __loader;
        }

        @Override
        public String typeName () {
            return JavaNames.internalToType (__internalName);
        }

        @Override
        public String internalName () {
            return __internalName;
        }

        @Override
        public boolean isPrimitive() {
            return false;
        }

        @Override
        public boolean isArray () {
            return false;
        }

        @Override
        public boolean isInterface () {
            return Modifier.isInterface (__modifiers);
        }

        @Override
        public Optional <Class> superClass () {
            if (__superName.isPresent ()) {
                final Optional <Class> result = classLoader ().classForType (__superName.get ().asSymbol());
                if (result.isEmpty()) {
                    throw new MissingClassException (
                        "super class missing: %s", __superName.get().asInternalName()
                    );
                }

                return result;

            } else {
                // This is the Object class.
                return Optional.empty ();
            }
        }

        @Override
        public Stream <Class> interfaces () {
            // Convert interface names to classes lazily.
            return __interfaces.stream ().map (itfName -> {
                final Optional <Class> result =__loader.classForType (itfName.asSymbol());
                if (result.isEmpty()) {
                    throw new MissingClassException (
                        "interface missing: %s", itfName.asInternalName()
                    );
                }

                return result.get ();
            });
        }


        @Override
        public Stream <ClassDesc> interfaceTypes () {
            // Convert interface names to types.
            return __interfaces.stream ().map (ClassEntry::asSymbol);
        }

        //

        @Override
        public Optional <Method> methodForSignature (final String sig) {
            return Optional.ofNullable (__methods.get (sig));
        }
    }

    //

    static final class ArrayClass implements Class {

        private final ClassDesc __type;

        private ArrayClass (final ClassDesc type) {
            __type = type;
        }

        @Override
        public ClassLoader classLoader () {
            return __root__;
        }

        // if the type is an array of one or more dimension it return the type of the most inner element the array,
        // otherwise it return the element itself
        // e.g. input -> ClassDesc of Object[][]    return -> ClassDesc of Object
        private static ClassDesc getArrayType(ClassDesc classDesc) {
            if (classDesc.isArray()) {
                return getArrayType(classDesc.componentType());
            }
            return classDesc;
        }

        @Override
        public String typeName () {
            ClassDesc componentType = getArrayType(__type);
            String pkg = componentType.packageName();
            if (!pkg.isEmpty()) {
                return pkg + "." + __type.displayName();
            }
            return __type.displayName();
        }

        @Override
        public String internalName () {
            return __type.displayName();
        }// TODO this is not used

        @Override
        public boolean isPrimitive () {
            return false;
        }

        @Override
        public boolean isArray () {
            return true;
        }

        @Override
        public boolean isInterface () {
            return false;
        }

        @Override
        public Optional <Class> superClass () {
            final ClassDesc classDesc = CD_Object;
            final Optional <Class> result = __root__.classForType(classDesc);
            if (result.isEmpty()) {
                throw new MissingClassException (
                    "super class missing: %s", classDesc.descriptorString()
                        // TODO: this is not equivalent to superType.getInternalName (), but I believe it should not matter as it is just an info
                );
            }
            return result;
        }


        @Override
        public Stream <Class> interfaces () {
            return Stream.empty ();
        }


        @Override
        public Stream <ClassDesc> interfaceTypes () {
            return Stream.empty ();
        }

        @Override
        public Optional <Method> methodForSignature (final String sig) {
            return Optional.empty ();
        }

    }


    public static final class Method {
        private final Class __class;
        private final String __sig;
        private final String __name;
        private final MethodTypeDesc __type;
        private final int __modifiers;

        private Method(final Class cls, final MethodModel mm) {
            __class = cls;
            __name = mm.methodName().stringValue();
            __modifiers = mm.flags().flagsMask();

            __type = mm.methodTypeSymbol();

            __sig = mm.methodName().stringValue() + mm.methodType().stringValue();  // TODO is this ok??
        }

        //

        public String name () {
            return __name;
        }

        public MethodTypeDesc type () {
            return __type;
        }

        public Optional <Class> returnType () {
            final ClassLoader cl = __class.classLoader ();
            final Optional <Class> result = cl.classForType (__type.returnType());
            if (result.isEmpty()) {
                throw new MissingClassException (
                    "return type class missing: %s", __type.descriptorString()
                );
            }

            return result;
        }

        public String signature () {
            return __sig;
        }

        /**
         * Returns {@code true} if this method is static.
         */
        public boolean isStatic () {
            return Modifier.isStatic (__modifiers);
        }

        /**
         * Returns {@code true} if this method is native.
         */
        public boolean isNative () {
            return Modifier.isNative (__modifiers);
        }

    }

}
