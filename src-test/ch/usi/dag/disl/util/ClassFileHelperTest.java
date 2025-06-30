package ch.usi.dag.disl.util;

import ch.usi.dag.disl.TestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.lang.classfile.*;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.List;

import static java.lang.constant.ConstantDescs.CD_void;

public class ClassFileHelperTest {

    public enum TestEnum {
        FIRST,
        SECOND
    }

    public static class TestClass {

        final boolean a = true;
        final int b = 10;
        final byte c = 1;
        final char d = 'a';
        final TestEnum e = TestEnum.FIRST;
        final String f = "F";

        public void loadConstant() {
            doNothing(a);
            doNothing(b);
            doNothing(c);
            doNothing(d);
//            doNothing(TestEnum.FIRST);
            doNothing(f);
        }

        public void doNothing(Object o) {}

        public int someMath(int i) {
            int a = 10;
            return otherMath(a, i) % 2;
        }

        public int otherMath(int a, int b) {
            return a * 2 + b / 2;
        }
    }

    private final ClassModel testClass = TestUtils.__loadClass(TestClass.class);

    @Test
    public void loadConstantTest() throws ReflectiveOperationException {
        MethodModelCopy method = TestUtils.__getMethod(testClass, "loadConstant");
        List<CodeElement> codeElements = method.instructions;

        for (CodeElement element: codeElements) {
            if (element instanceof ConstantInstruction constantInstruction) {
                Object constant = constantInstruction.constantValue().resolveConstantDesc(MethodHandles.lookup());
                ConstantInstruction constantElement = (ConstantInstruction) ClassFileHelper.loadConst(constant);
                Assert.assertEquals(constantElement.opcode(), constantInstruction.opcode());
                Assert.assertEquals(constantElement.typeKind(), constantInstruction.typeKind());
            }
        }
        Assert.assertEquals(Opcode.ICONST_1, ClassFileHelper.loadConst(true).opcode());
        Assert.assertEquals(Opcode.ICONST_0, ClassFileHelper.loadConst(false).opcode());
        Assert.assertEquals(Opcode.ICONST_2, ClassFileHelper.loadConst(2).opcode());
        Assert.assertEquals(Opcode.ICONST_3, ClassFileHelper.loadConst(3).opcode());
        Assert.assertEquals(Opcode.BIPUSH, ClassFileHelper.loadConst(Byte.MAX_VALUE).opcode());
        Assert.assertEquals(Opcode.SIPUSH, ClassFileHelper.loadConst(Short.MAX_VALUE).opcode());
        Assert.assertEquals(Opcode.LDC, ClassFileHelper.loadConst(40000).opcode());
        Assert.assertEquals(Opcode.LDC, ClassFileHelper.loadConst("Hello").opcode());
        Assert.assertEquals(Opcode.LDC, ClassFileHelper.loadConst(new Object()).opcode());
        Assert.assertEquals(Opcode.LDC, ClassFileHelper.loadConst(new Object[10]).opcode());

    }

    @Test
    public void getOperandsTest() {
        MethodModelCopy method = TestUtils.__getMethod(testClass, "loadConstant");
        List<CodeElement> codeElements = method.instructions;
        List<ConstantInstruction> constantInstructions = codeElements.stream()
                .filter(i -> i instanceof ConstantInstruction)
                .map(i -> (ConstantInstruction) i).toList();

        ConstantInstruction intLoad = constantInstructions.get(1);
        ConstantInstruction stringLoad = constantInstructions.get(4);
        Assert.assertEquals(Integer.valueOf(10), ClassFileHelper.getIntConstantOperand(intLoad));
        Assert.assertEquals("F", ClassFileHelper.getStringConstOperand(stringLoad));
    }


    @Test
    public void getDimensionTest() {
        ClassDesc zeroDimension = ClassDesc.ofDescriptor(List.class.descriptorString());
        ClassDesc oneDimension = ClassDesc.ofDescriptor(int[].class.descriptorString());
        ClassDesc twoDimension = ClassDesc.ofDescriptor(int[][].class.descriptorString());
        ClassDesc threeDimension = ClassDesc.ofDescriptor(int[][][].class.descriptorString());
        Assert.assertEquals(0, ClassFileHelper.getDimensions(zeroDimension));
        Assert.assertEquals(1, ClassFileHelper.getDimensions(oneDimension));
        Assert.assertEquals(2, ClassFileHelper.getDimensions(twoDimension));
        Assert.assertEquals(3, ClassFileHelper.getDimensions(threeDimension));
    }

    @Test
    public void getElementTypeTest() {
        ClassDesc intType = ClassDesc.ofDescriptor(int[].class.descriptorString());
        ClassDesc boolType = ClassDesc.ofDescriptor(boolean[].class.descriptorString());
        ClassDesc objectType = ClassDesc.ofDescriptor(Object[].class.descriptorString());
        Assert.assertEquals("I", ClassFileHelper.getElementType(intType));
        Assert.assertEquals("Z", ClassFileHelper.getElementType(boolType));
        Assert.assertEquals("Ljava/lang/Object;", ClassFileHelper.getElementType(objectType));
    }

    @Test
    public void getClassNameTest() {
        ClassDesc intName = ClassDesc.ofDescriptor(int.class.descriptorString());
        ClassDesc stringName = ClassDesc.ofDescriptor(String.class.descriptorString());
        ClassDesc objArrName = ClassDesc.ofDescriptor(Object[].class.descriptorString());
        Assert.assertEquals("int", ClassFileHelper.getClassName(intName));
        Assert.assertEquals("java.lang.String", ClassFileHelper.getClassName(stringName));
        Assert.assertEquals("java.lang.Object[]", ClassFileHelper.getClassName(objArrName));
    }

    @Test
    public void getInternalNameTest() {
        ClassDesc intName = ClassDesc.ofDescriptor(int.class.descriptorString());
        ClassDesc stringName = ClassDesc.ofDescriptor(String.class.descriptorString());
        ClassDesc objArrName = ClassDesc.ofDescriptor(Object[].class.descriptorString());
        Assert.assertEquals("I", ClassFileHelper.getInternalName(intName));
        Assert.assertEquals("java/lang/String", ClassFileHelper.getInternalName(stringName));
        Assert.assertEquals("[Ljava/lang/Object;", ClassFileHelper.getInternalName(objArrName));
    }

    @Test
    public void getMaxStackTest() {
        List<MethodModel> methodModelList = testClass.methods();
        for (MethodModel method: methodModelList) {
            CodeModel code = method.code().orElseThrow();
            List<CodeElement> elements = ClassFileInstructionClone.copyList(code.elementList(), false);
            List<ExceptionCatch> exceptionCatches = code.exceptionHandlers();
            CodeAttribute attribute = method.findAttribute(Attributes.code()).orElseThrow();
            int maxStack = attribute.maxStack();
            int maxLocals = attribute.maxLocals();
            Assert.assertEquals(maxStack, ClassFileHelper.getMaxStack(elements, exceptionCatches));
            Assert.assertEquals(maxLocals, ClassFileHelper.getMaxLocals(elements, method.methodTypeSymbol(), method.flags()));
        }
    }

    @Test
    public void getStringConstOperandTest() {
        String s = "Hello";
        LoadableConstantEntry entry = ConstantPoolBuilder.of().stringEntry(s);
        LoadableConstantEntry entry1 = ConstantPoolBuilder.of().intEntry(10);
        ConstantInstruction.LoadConstantInstruction loadConstantInstruction = ConstantInstruction.ofLoad(Opcode.LDC, entry);
        ConstantInstruction.LoadConstantInstruction loadConstantInstruction1 = ConstantInstruction.ofLoad(Opcode.LDC, entry1);
        String result = ClassFileHelper.getStringConstOperand(loadConstantInstruction);
        String resultEmpty = ClassFileHelper.getStringConstOperand(loadConstantInstruction1);
        Assert.assertEquals(s, result);
        Assert.assertNull(resultEmpty);
    }

    @Test
    public void getIntConstOperand() {
        ConstantInstruction bipush = ConstantInstruction.ofArgument(Opcode.BIPUSH, 100);
        ConstantInstruction iconst = ConstantInstruction.ofIntrinsic(Opcode.ICONST_5);
        LoadableConstantEntry entry = ConstantPoolBuilder.of().intEntry(1000000);
        ConstantInstruction ldc = ConstantInstruction.ofLoad(Opcode.LDC, entry);
        ConstantInstruction lconst = ConstantInstruction.ofIntrinsic(Opcode.LCONST_1);
        Assert.assertEquals(Integer.valueOf(100), ClassFileHelper.getIntConstantOperand(bipush));
        Assert.assertEquals(Integer.valueOf(5), ClassFileHelper.getIntConstantOperand(iconst));
        Assert.assertEquals(Integer.valueOf(1000000), ClassFileHelper.getIntConstantOperand(ldc));
        Assert.assertNull(ClassFileHelper.getIntConstantOperand(lconst));
    }

    public static class BranchClass {
        public void t(int i) {
            if (i > 0) {
                System.out.println("A");
            }
        }

        public void h(int i) {
            switch (i) {
                case 1 -> System.out.println("1");
                case 2 -> System.out.println("2");
                case 3 -> System.out.println("3");
                default -> System.out.println("default");
            }
        }
    }

    @Test
    public void replaceBranchAndLabelsTargetTest() {
        // this test need a codeBuilder and creating (or transforming) is basically the only way to get one.
        ClassFile.of().build(ClassDesc.of("Test"), classBuilder -> {
            classBuilder.withMethodBody("A", MethodTypeDesc.of(CD_void), AccessFlag.PUBLIC.mask(), codeBuilder -> {

                ClassModel classModel = TestUtils.__loadClass(BranchClass.class);
                MethodModelCopy methodModel = TestUtils.__getMethod(classModel, "t");
                List<CodeElement> instructions = methodModel.instructions;

                List<CodeElement> d = new ArrayList<>();
                d.addAll(instructions);
                d.addAll(instructions);

                Assert.assertTrue(ClassFileHelper.findDoubleLabel(d));

                List<CodeElement> e = new ArrayList<>();
                e.addAll(ClassFileHelper.replaceBranchAndLabelsTarget(instructions, codeBuilder));
                e.addAll(ClassFileHelper.replaceBranchAndLabelsTarget(instructions, codeBuilder));

                Assert.assertFalse(ClassFileHelper.findDoubleLabel(e));

                Assert.assertEquals(d.size(), e.size());

                for (int i = 0; i < d.size(); i++) {
                    CodeElement dElement = d.get(i);
                    CodeElement eElement = e.get(i);
                    if (dElement instanceof Instruction instruction) {
                        Assert.assertTrue(eElement instanceof Instruction);
                        Assert.assertEquals(instruction.opcode(), ((Instruction) eElement).opcode());
                    } else {
                        Assert.assertFalse(eElement instanceof Instruction);
                    }
                }

                codeBuilder.return_();
            });
        });
    }

    @Test
    public void replaceBranchAndLabelsTargetTest2() {
        // this test need a codeBuilder and creating (or transforming) is basically the only way to get one.
        ClassFile.of().build(ClassDesc.of("Test"), classBuilder -> {
            classBuilder.withMethodBody("A", MethodTypeDesc.of(CD_void), AccessFlag.PUBLIC.mask(), codeBuilder -> {

                ClassModel classModel = TestUtils.__loadClass(BranchClass.class);
                MethodModelCopy methodModel = TestUtils.__getMethod(classModel, "h");
                List<CodeElement> instructions = methodModel.instructions;

                List<CodeElement> d = new ArrayList<>();
                d.addAll(instructions);
                d.addAll(instructions);

                Assert.assertTrue(ClassFileHelper.findDoubleLabel(d));

                List<CodeElement> e = new ArrayList<>();
                e.addAll(ClassFileHelper.replaceBranchAndLabelsTarget(instructions, codeBuilder));
                e.addAll(ClassFileHelper.replaceBranchAndLabelsTarget(instructions, codeBuilder));

                Assert.assertFalse(ClassFileHelper.findDoubleLabel(e));

                Assert.assertEquals(d.size(), e.size());

                for (int i = 0; i < d.size(); i++) {
                    CodeElement dElement = d.get(i);
                    CodeElement eElement = e.get(i);
                    if (dElement instanceof Instruction instruction) {
                        Assert.assertTrue(eElement instanceof Instruction);
                        Assert.assertEquals(instruction.opcode(), ((Instruction) eElement).opcode());
                    } else {
                        Assert.assertFalse(eElement instanceof Instruction);
                    }
                }

                codeBuilder.return_(); // this is just so that the test do not crash
            });
        });
    }

}
