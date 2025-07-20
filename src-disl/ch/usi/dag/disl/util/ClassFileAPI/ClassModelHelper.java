package ch.usi.dag.disl.util.ClassFileAPI;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.util.List;

public enum ClassModelHelper {
    // TODO: this class tries to mimic CLassNodeHelper

    OUTLINE(List.of(
            ClassFile.DebugElementsOption.DROP_DEBUG,  // this should be equivalent to ClassReader.SKIP_DEBUG
            ClassFile.StackMapsOption.DROP_STACK_MAPS,  // this should be equivalent to ClassReader.SKIP_FRAMES
            ClassFile.AttributesProcessingOption.DROP_UNSTABLE_ATTRIBUTES  // TODO I am not sure if this is equivalent to ClassReader.SKIP_CODE
    )),

    SNIPPET(List.of(
            ClassFile.DebugElementsOption.PASS_DEBUG,  // this should be the default, but is just to be sure
            ClassFile.StackMapsOption.DROP_STACK_MAPS
    )),

    FULL(List.of(
            ClassFile.StackMapsOption.GENERATE_STACK_MAPS  // this should be equivalent to ClassReader.EXPAND_FRAMES
    )),

    DEFAULT(List.of()),  // use the default options of the classFile

    DROPLINES(List.of(ClassFile.LineNumbersOption.DROP_LINE_NUMBERS));


    private final List<ClassFile.Option> __options;

    private ClassModelHelper(final List<ClassFile.Option> options) {__options = options;}

    public ClassModel load(final String className) throws IOException {
        // this is what asm does internally
        String resource = className.replace(".", "/") + ".class";
        InputStream inputStream = ClassLoader.getSystemResourceAsStream(resource);
        if (inputStream == null) {
            throw new RuntimeException("Input stream is null while loading class " + className);
        }
        byte[] classBytes = inputStream.readAllBytes();
        ClassFile.Option[] options = __options.toArray(ClassFile.Option[]::new);
        return ClassFile.of(options).parse(classBytes);
    }

    public ClassModel unmarshal(final byte[] bytes) {
        ClassFile.Option[] options = __options.toArray(ClassFile.Option[]::new);
        return ClassFile.of(options).parse(bytes);
    }

    // Note the method marshal is not present because the ClassFile use a builder pattern
    // to make new byte[]

    public List<ClassFile.Option> getOptions() {
        return __options;
    }
}
