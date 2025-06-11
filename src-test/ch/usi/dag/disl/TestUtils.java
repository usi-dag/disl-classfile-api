package ch.usi.dag.disl;

import ch.usi.dag.disl.util.ClassFileAPI.ClassModelHelper;
import ch.usi.dag.disl.util.MethodModelCopy;

import java.lang.classfile.ClassModel;

/**
 * Utility class for various tests
 */
public class TestUtils {

    public static MethodModelCopy __getMethod(final ClassModel classModel, final String methodName) {
        return new MethodModelCopy(classModel.methods().stream().filter(m -> m.methodName().equalsString(methodName)).findFirst().orElseThrow());
    }

    public static ClassModel __loadClass(Class<?> c) {
        try {
            return ClassModelHelper.DROPLINES.load(c.getName());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

    }
}
