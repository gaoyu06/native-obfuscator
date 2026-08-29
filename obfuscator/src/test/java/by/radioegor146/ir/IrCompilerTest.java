package by.radioegor146.ir;

import by.radioegor146.CodegenMode;
import by.radioegor146.HiddenMethodsPool;
import by.radioegor146.MethodContext;
import by.radioegor146.MethodProcessor;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.Platform;
import by.radioegor146.bytecode.IndyPreprocessor;
import by.radioegor146.bytecode.MethodHandleUtils;
import by.radioegor146.bytecode.PreprocessorUtils;
import by.radioegor146.helpers.ProcessHelper;
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import by.radioegor146.source.ClassSourceBuilder;
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IrCompilerTest {
    private static final int[] POST_CHAIN_INT_COMPARE_OPCODES = {
            Opcodes.IFEQ, Opcodes.IFLT, Opcodes.IFGE,
            Opcodes.IFGT, Opcodes.IFLE,
            Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE,
            Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE,
            Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE
    };

    private final AsmToIr frontend = new AsmToIr();
    private final IrCppEmitter emitter = new IrCppEmitter();

    private static String identityString(String value) {
        return value;
    }

    private static String acceptCharSequence(CharSequence value) {
        return value.toString();
    }

    private static Object returnObject(String value) {
        return new Object();
    }

    @Test
    public void buildsAndEmitsAdd() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;

        IrMethod ir = frontend.build("example/Math", method);

        assertEquals(
                "method example/Math.add(II)I -> i32 [static]\n"
                        + "  params: %arg0:i32, %arg1:i32\n"
                        + "block b0:\n"
                        + "  %v2:i32 = iadd %arg0, %arg1\n"
                        + "  return %v2\n",
                ir.toString());

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("// IR codegen: example/Math.add(II)I"));
        assertTrue(cpp.contains("jint v2;"));
        assertTrue(cpp.contains("(uint32_t) arg0 + (uint32_t) arg1"));
        assertTrue(cpp.contains("return v2;"));
    }

    @Test
    public void emitsWrappingSubtractAndMultiply() {
        String cpp = emitter.emitBody(frontend.build("example/Math", subMulMethod()));

        assertTrue(cpp.contains("(uint32_t) arg0 - (uint32_t) arg1"));
        assertTrue(cpp.contains("(uint32_t) v2 * (uint32_t) arg1"));
    }

    @Test
    public void lowersIntDivideAndRemainderWithoutCppUndefinedBehavior() {
        MethodNode method = divRemMethod();
        IrMethod ir = frontend.build("example/Math", method);
        String pretty = ir.toString();
        assertTrue(pretty.contains(" = idiv "));
        assertTrue(pretty.contains(" = irem "));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("utils::throw_re"));
        assertTrue(cpp.contains("== 0"));
        assertTrue(cpp.contains("== ((jint) 0x80000000U)"));
        assertTrue(cpp.contains("== -1"));
        assertTrue(cpp.contains("(int32_t) arg0 / (int32_t) arg1"));
        assertTrue(cpp.contains(" % (int32_t)"));
        assertFalse(cpp.contains("juint"));
    }

    @Test
    public void divideByZeroInsideTryUsesSharedCatchDispatch() {
        MethodNode method = divideCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        IrBlock divideBlock = ir.getBlocks().stream()
                .filter(block -> block.getInstructions().stream()
                        .anyMatch(instruction -> instruction instanceof IrNodes.IntDivRem))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("java/lang/ArithmeticException",
                divideBlock.getExceptionEdges().get(0).getCatchType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        int zeroGuard = cpp.indexOf("== 0");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", zeroGuard);
        int ordinaryDivide = cpp.indexOf("(int32_t) arg0 / (int32_t) arg1", zeroGuard);
        int swallowedReturn = cpp.indexOf("return 0;", zeroGuard);
        assertTrue(zeroGuard >= 0 && dispatch > zeroGuard && ordinaryDivide > dispatch);
        assertTrue(swallowedReturn < 0 || swallowedReturn > ordinaryDivide);
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->IsInstanceOf((jobject) caught_exception"));
    }

    @Test
    public void lowersLongDivideAndRemainderWithoutCppUndefinedBehavior() {
        MethodNode method = longDivRemMethod();
        IrMethod ir = frontend.build("example/Math", method);
        String pretty = ir.toString();
        assertTrue(pretty.contains(" = ldiv "));
        assertTrue(pretty.contains(" = lrem "));

        List<IrNodes.LongDivRem> divRems = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.LongDivRem.class::isInstance)
                .map(IrNodes.LongDivRem.class::cast)
                .collect(Collectors.toList());
        assertEquals(2, divRems.size());
        for (IrNodes.LongDivRem divRem : divRems) {
            assertEquals(IrType.I64, divRem.getResult().getType());
            assertEquals(IrType.I64, divRem.getLeft().getType());
            assertEquals(IrType.I64, divRem.getRight().getType());
        }

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("utils::throw_re"));
        assertTrue(cpp.contains("== 0"));
        assertTrue(cpp.contains("== ((jlong) 0x8000000000000000ULL)"));
        assertTrue(cpp.contains("== -1LL"));
        assertTrue(cpp.contains("(int64_t) arg0 / (int64_t) arg1"));
        assertTrue(cpp.contains(" % (int64_t)"));
        assertTrue(cpp.contains("= ((jlong) 0x8000000000000000ULL)"));
        assertTrue(cpp.contains("= 0LL"));
        assertFalse(cpp.contains("julong"));
    }

    @Test
    public void lowersLongNegateThroughUnsignedCarrierWrappingMinValue() {
        MethodNode method = longNegateMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.toString().contains(" = lneg "));

        IrNodes.LongUnary negate = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.LongUnary.class::isInstance)
                .map(IrNodes.LongUnary.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrType.I64, negate.getResult().getType());
        assertEquals(IrType.I64, negate.getOperand().getType());

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("(jlong) (-(uint64_t) arg0)"));
        assertFalse(cpp.contains("julong"));
    }

    @Test
    public void longDivideByZeroInsideTryUsesSharedCatchDispatch() {
        MethodNode method = longDivideCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        IrBlock divideBlock = ir.getBlocks().stream()
                .filter(block -> block.getInstructions().stream()
                        .anyMatch(instruction -> instruction instanceof IrNodes.LongDivRem))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("java/lang/ArithmeticException",
                divideBlock.getExceptionEdges().get(0).getCatchType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        int zeroGuard = cpp.indexOf("== 0");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", zeroGuard);
        int ordinaryDivide = cpp.indexOf("(int64_t) arg0 / (int64_t) arg1", zeroGuard);
        assertTrue(zeroGuard >= 0 && dispatch > zeroGuard && ordinaryDivide > dispatch);
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->IsInstanceOf((jobject) caught_exception"));
    }

    @Test
    public void integratedEmitterUsesExistingJniSignatureStyleWithoutLegacySlots() {
        MethodNode method = addMethod();
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = "example/Math";
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains(
                "jint JNICALL __ngen_native_add0(JNIEnv *env, jclass clazz, jint arg0, jint arg1)"));
        assertTrue(cpp.contains("// IR codegen: example/Math.add(II)I"));
        assertFalse(cpp.contains("cstack"));
        assertFalse(cpp.contains("clocal"));
    }

    @Test
    public void emitsFloatAndDoubleJniBoundaryTypes() {
        String floatCpp = compileToCpp(staticPrimitiveInvokeMethod(
                "floatBoundary", "identityFloat", "(F)F"), 0);
        assertTrue(floatCpp.contains("jfloat JNICALL __ngen_native_floatBoundary0("
                + "JNIEnv *env, jclass clazz, jfloat arg0)"));

        String doubleCpp = compileToCpp(staticPrimitiveInvokeMethod(
                "doubleBoundary", "identityDouble", "(D)D"), 1);
        assertTrue(doubleCpp.contains("jdouble JNICALL __ngen_native_doubleBoundary1("
                + "JNIEnv *env, jclass clazz, jdouble arg0)"));
    }

    @Test
    public void buildsLoopWithHeaderPhisAndEmitsBranches() {
        MethodNode method = sumToMethod();

        IrMethod ir = frontend.build("example/Math", method);

        assertEquals(4, ir.getBlocks().size());
        IrBlock header = ir.getBlocks().get(1);
        IrPhi sum = header.getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.LOCAL
                        && phi.getSlotIndex() == 1)
                .findFirst().orElseThrow(AssertionError::new);
        IrPhi index = header.getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.LOCAL
                        && phi.getSlotIndex() == 2)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, sum.getIncoming().size());
        assertEquals(2, index.getIncoming().size());
        assertTrue(header.getTerminator() instanceof IrNodes.Branch);

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("B1:"));
        assertTrue(cpp.contains("if (v3 >= v1) {"));
        assertTrue(cpp.contains("goto B1;"));
        assertTrue(cpp.contains("return v8;"));
    }

    @Test
    public void lowersIntFieldsAndSimpleInvokes() {
        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode owner = owner();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));

        MethodContext increment = new MethodContext(obfuscator, incrementFieldMethod(),
                0, owner, 0);
        compiler.processMethod(increment);
        String fieldCpp = increment.output.toString();
        assertTrue(fieldCpp.contains("env->GetFieldID"));
        assertTrue(fieldCpp.contains("env->GetIntField"));
        assertTrue(fieldCpp.contains("env->SetIntField"));

        MethodContext staticInvoke = new MethodContext(obfuscator, staticInvokeMethod(),
                1, owner, 0);
        compiler.processMethod(staticInvoke);
        assertTrue(staticInvoke.output.toString().contains("env->CallStaticIntMethod"));

        MethodContext virtualInvoke = new MethodContext(obfuscator, virtualInvokeMethod(),
                2, owner, 0);
        compiler.processMethod(virtualInvoke);
        assertTrue(virtualInvoke.output.toString().contains("env->CallIntMethod"));
    }

    @Test
    public void lowersPreprocessorIntrinsicsAndMethodHandleInvokesInIr() {
        NativeObfuscator obfuscator = new NativeObfuscator();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));

        MethodContext locals = new MethodContext(obfuscator,
                preprocessorLocalsMethod(), 0, owner(), 0);
        compiler.processMethod(locals);
        assertTrue(locals.output.toString().contains("utils::get_lookup(env, clazz)"));
        assertTrue(locals.output.toString().contains("= classloader;"));
        assertTrue(locals.output.toString().contains("= clazz;"));
        assertFalse(locals.output.toString().contains("native.magic"));

        MethodContext linkCallSite = new MethodContext(obfuscator,
                linkCallSiteMethod(), 1, owner(), 0);
        compiler.processMethod(linkCallSite);
        assertTrue(linkCallSite.output.toString().contains("utils::link_call_site(env"));
        assertFalse(linkCallSite.output.toString().contains("native.magic"));

        MethodContext invokeReverse = new MethodContext(obfuscator,
                invokeReverseMethod(), 2, owner(), 0);
        compiler.processMethod(invokeReverse);
        assertTrue(invokeReverse.output.toString().contains("env->CallStaticIntMethod"));
        assertFalse(invokeReverse.output.toString().contains("native.magic"));

        ClassNode invokeExactOwner = owner();
        MethodContext invokeExact = new MethodContext(obfuscator,
                methodHandleInvokeExactMethod(), 3, invokeExactOwner, 0);
        compiler.processMethod(invokeExact);
        assertTrue(invokeExact.output.toString().contains("env->CallStaticIntMethod"));
        assertFalse(invokeExact.output.toString().contains("\"invokeExact\""));

        assertEquals(1, obfuscator.getHiddenMethodsPool().getClasses().size());
        ClassNode hidden = obfuscator.getHiddenMethodsPool().getClasses().get(0);
        assertEquals("native0/hidden/Hidden0", hidden.name);
        assertTrue(hidden.methods.stream()
                .anyMatch(method -> method.name.startsWith("invokereverse")));
        assertTrue(invokeExactOwner.methods.stream()
                .anyMatch(method -> method.name.startsWith("mhinvokeexact")));
    }

    @Test
    public void acceptsTypeDescriptorBootstrapParameterDuringIndyPreprocessing() {
        MethodNode method = typeDescriptorBootstrapMethod();
        new IndyPreprocessor().process(owner(), method, Platform.STD_JAVA);

        boolean callsBootstrap = false;
        for (org.objectweb.asm.tree.AbstractInsnNode instruction
                : method.instructions.toArray()) {
            if (instruction instanceof LdcInsnNode
                    && "Wrong 3 first arguments in bsm".equals(
                    ((LdcInsnNode) instruction).cst)) {
                throw new AssertionError("TypeDescriptor bootstrap was rejected");
            }
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                callsBootstrap |= "example/Bootstrap".equals(invoke.owner)
                        && "bootstrap".equals(invoke.name);
            }
        }
        assertTrue(callsBootstrap);
    }

    @Test
    public void generatedInvokeExactHelperPreservesExactMethodTypeChecks() throws Throwable {
        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode exactOwner = owner();
        exactOwner.version = Opcodes.V1_8;
        String callSiteDescriptor = "(Ljava/lang/String;)Ljava/lang/String;";
        HiddenMethodsPool.HiddenMethod exactHelper = MethodHandleUtils.getInvokeHelper(
                obfuscator, exactOwner, "invokeExact", callSiteDescriptor);
        HiddenMethodsPool.HiddenMethod invokeHelper = MethodHandleUtils.getInvokeHelper(
                obfuscator, exactOwner, "invoke", callSiteDescriptor);

        assertEquals(exactOwner, exactHelper.getClassNode());
        assertEquals(exactOwner, invokeHelper.getClassNode());
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());

        ClassWriter exactWriter = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        exactOwner.accept(exactWriter);
        Class<?> exactHelperClass =
                new ByteArrayClassLoader().define(exactWriter.toByteArray());
        Method exact = exactHelperClass.getMethod(exactHelper.getMethodNode().name,
                MethodHandle.class, String.class);
        Method invoke = exactHelperClass.getMethod(invokeHelper.getMethodNode().name,
                MethodHandle.class, String.class);

        MethodHandle exactTarget = MethodHandles.lookup().findStatic(
                IrCompilerTest.class, "identityString",
                MethodType.methodType(String.class, String.class));
        MethodHandle adaptableTarget = MethodHandles.lookup().findStatic(
                IrCompilerTest.class, "acceptCharSequence",
                MethodType.methodType(String.class, CharSequence.class));
        MethodHandle wrongReturnTarget = MethodHandles.lookup().findStatic(
                IrCompilerTest.class, "returnObject",
                MethodType.methodType(Object.class, String.class));

        assertEquals("value", exact.invoke(null, exactTarget, "value"));
        assertEquals("value", invoke.invoke(null, adaptableTarget, "value"));
        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> exact.invoke(null, adaptableTarget, "value"));
        assertTrue(error.getCause() instanceof WrongMethodTypeException);
        InvocationTargetException wrongReturn = assertThrows(
                InvocationTargetException.class,
                () -> invoke.invoke(null, wrongReturnTarget, "value"));
        assertTrue(wrongReturn.getCause() instanceof ClassCastException);
    }

    @Test
    public void lowersNewAndConstructorInvoke() {
        MethodNode method = constructObjectMethod();
        IrMethod ir = frontend.build("example/Math", method);
        String pretty = ir.toString();
        assertTrue(pretty.contains(" = new java/lang/Object"));
        assertTrue(pretty.contains(
                "invokespecial java/lang/Object.<init>()V"));
        IrNodes.Invoke constructor = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.Invoke.class::isInstance)
                .map(IrNodes.Invoke.class::cast)
                .filter(invoke -> invoke.getKind() == IrNodes.Invoke.Kind.SPECIAL)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(null, constructor.getResult());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->AllocObject(cclasses["));
        assertTrue(cpp.contains("env->GetMethodID"));
        assertTrue(cpp.contains("env->CallNonvirtualVoidMethod"));
        assertTrue(cpp.contains("env->CallIntMethod"));
        assertTrue(cpp.contains("env->ExceptionCheck()"));
    }

    @Test
    public void admitsSimpleConstructorOnlyForIrAndKeepsGetterOnIr() {
        MethodNode constructor = intFieldConstructor();
        ClassNode owner = constructorOwner("example/Math", "java/lang/Object");

        assertFalse(MethodProcessor.shouldProcess(constructor));
        assertFalse(MethodProcessor.shouldProcess(constructor, CodegenMode.LEGACY));
        assertTrue(MethodProcessor.shouldProcess(constructor, CodegenMode.IR));

        IrMethod completeIr = frontend.build(owner.name, constructor);
        assertEquals(IrType.VOID, completeIr.getReturnType());
        assertEquals(IrType.REFERENCE, completeIr.getParameters().get(0).getType());
        NativeObfuscator completeObfuscator = new NativeObfuscator();
        String completeCpp = emitter.emitBody(completeIr,
                new MethodContext(completeObfuscator, constructor, 0, owner, 0));
        assertTrue(completeCpp.contains("env->CallNonvirtualVoidMethod"));
        assertTrue(completeCpp.contains("env->SetIntField"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext constructorContext =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        IrMethodCompiler compiler =
                new IrMethodCompiler(new MethodShellEmitter(obfuscator));
        compiler.processMethod(constructorContext);

        String constructorCpp = constructorContext.output.toString();
        assertTrue(constructorCpp.contains(
                "void JNICALL __ngen_special_init_0_0(JNIEnv *env, "
                        + "jobject ignored_hidden, jobject obj, jint arg0)"));
        assertTrue(constructorCpp.contains(
                "// IR codegen: example/Math.<init>(I)V"));
        assertTrue(constructorCpp.contains("env->SetIntField(obj"));
        assertTrue(constructorCpp.contains("if (env->ExceptionCheck())"));
        assertTrue(constructorCpp.contains("return;"));
        assertEquals(0, constructor.access & Opcodes.ACC_NATIVE);
        assertTrue(constructorContext.proxyMethod != null);
        assertTrue((constructorContext.proxyMethod.getMethodNode().access
                & Opcodes.ACC_NATIVE) != 0);
        assertEquals(Arrays.asList(Opcodes.ALOAD, Opcodes.INVOKESPECIAL,
                        Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.INVOKESTATIC,
                        Opcodes.RETURN),
                realOpcodes(constructor));

        MethodNode getter = intFieldGetter();
        MethodContext getterContext =
                new MethodContext(obfuscator, getter, 1, owner, 0);
        compiler.processMethod(getterContext);
        assertTrue(getterContext.output.toString().contains(
                "// IR codegen: example/Math.getValue()I"));
        assertTrue(getterContext.output.toString().contains("env->GetIntField(obj"));
        assertTrue((getter.access & Opcodes.ACC_NATIVE) != 0);
    }

    @Test
    public void lowersSubclassAndReferenceFieldConstructorBodies() {
        ClassNode subclass = constructorOwner("example/Child", "example/Base");
        MethodNode subclassConstructor = subclassConstructor();
        IrMethod subclassIr = frontend.build(subclass.name, subclassConstructor);
        NativeObfuscator fullObfuscator = new NativeObfuscator();
        String fullCpp = emitter.emitBody(subclassIr,
                new MethodContext(fullObfuscator, subclassConstructor, 0, subclass, 0));
        assertTrue(fullCpp.contains("env->CallNonvirtualVoidMethod(obj"));
        assertTrue(fullCpp.contains("env->SetLongField"));

        NativeObfuscator subclassObfuscator = new NativeObfuscator();
        MethodContext subclassContext = new MethodContext(
                subclassObfuscator, subclassConstructor, 0, subclass, 0);
        new IrMethodCompiler(new MethodShellEmitter(subclassObfuscator))
                .processMethod(subclassContext);
        assertTrue(subclassContext.output.toString().contains("env->SetLongField"));
        assertFalse(subclassContext.output.toString().contains("cstack"));
        assertEquals("example/Base",
                ((MethodInsnNode) subclassConstructor.instructions.get(2)).owner);
        assertEquals(Opcodes.INVOKESPECIAL,
                subclassConstructor.instructions.get(2).getOpcode());
        assertTrue(realOpcodes(subclassConstructor).contains(Opcodes.INVOKESTATIC));

        ClassNode delegatingOwner =
                constructorOwner("example/Math", "java/lang/Object");
        MethodNode delegatingConstructor = delegatingConstructor();
        NativeObfuscator delegatingObfuscator = new NativeObfuscator();
        MethodContext delegatingContext = new MethodContext(
                delegatingObfuscator, delegatingConstructor, 0, delegatingOwner, 0);
        new IrMethodCompiler(new MethodShellEmitter(delegatingObfuscator))
                .processMethod(delegatingContext);
        assertTrue(delegatingContext.output.toString().contains("env->SetIntField"));
        MethodInsnNode retainedThisCall =
                (MethodInsnNode) delegatingConstructor.instructions.get(2);
        assertEquals("example/Math", retainedThisCall.owner);
        assertEquals("<init>", retainedThisCall.name);
        assertEquals(Opcodes.INVOKESPECIAL, retainedThisCall.getOpcode());

        ClassNode holder = constructorOwner("example/Holder", "java/lang/Object");
        MethodNode referenceConstructor = referenceFieldConstructor();
        NativeObfuscator referenceObfuscator = new NativeObfuscator();
        MethodContext referenceContext = new MethodContext(
                referenceObfuscator, referenceConstructor, 0, holder, 0);
        new IrMethodCompiler(new MethodShellEmitter(referenceObfuscator))
                .processMethod(referenceContext);
        String referenceCpp = referenceContext.output.toString();
        assertEquals(2, countOccurrences(referenceCpp, "env->SetObjectField"));
        assertTrue(referenceCpp.contains("arg0"));
        assertTrue(referenceCpp.contains("arg1"));
    }

    @Test
    public void admitsFloatAndDoubleConstructorSuffixWithHiddenBridge() {
        MethodNode constructor = floatingFieldConstructor();
        ClassNode owner = constructorOwner("example/Math", "java/lang/Object");
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, constructor, 0, owner, 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("jobject ignored_hidden, jobject obj, "
                + "jfloat arg0, jdouble arg1"));
        assertTrue(cpp.contains("env->SetFloatField(obj"));
        assertTrue(cpp.contains("env->SetDoubleField("));
        assertEquals(0, constructor.access & Opcodes.ACC_NATIVE);
        assertTrue(context.proxyMethod != null);
        assertTrue((context.proxyMethod.getMethodNode().access & Opcodes.ACC_NATIVE) != 0);
        assertTrue(realOpcodes(constructor).contains(Opcodes.INVOKESTATIC));
    }

    @Test
    public void admitsPrefixLocalBranchConstructorWithHiddenBridge() {
        MethodNode constructor = prefixLocalBranchConstructor();
        ClassNode owner = constructorOwner("example/Validated", "example/Base");

        // The complete constructor (including the uninitialized-this prefix and
        // the this/super call) must build, and the split must produce the
        // initialized-this suffix without rejecting the prefix-local branch.
        frontend.build(owner.name, constructor);
        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(owner, constructor);
        assertTrue(nativeBody != null);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("void JNICALL __ngen_special_init_0_0(JNIEnv *env, "
                + "jobject ignored_hidden, jobject obj, jint arg0)"));
        assertEquals(0, constructor.access & Opcodes.ACC_NATIVE);
        assertTrue(context.proxyMethod != null);
        assertTrue((context.proxyMethod.getMethodNode().access
                & Opcodes.ACC_NATIVE) != 0);

        // The prefix-local branch and the this/super call stay in bytecode; the
        // suffix is reached through the hidden bridge, so the constructor is no
        // longer its original full body.
        assertTrue(realOpcodes(constructor).contains(Opcodes.IFNE));
        String bridgeName = context.proxyMethod.getMethodNode().name;
        assertTrue(retainsBridgeInvocation(constructor, bridgeName));
        MethodInsnNode retainedSuper = retainedThisOrSuperCall(constructor, owner);
        assertEquals("example/Base", retainedSuper.owner);
        assertEquals("<init>", retainedSuper.name);
        assertEquals(Opcodes.INVOKESPECIAL, retainedSuper.getOpcode());
    }

    @Test
    public void admitsIdentityPreservingPrefixAstoreZeroWithHiddenBridge() {
        ClassNode owner = constructorOwner(
                "example/IdentityReceiver", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                identityAstoreZeroConstructor(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("()V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.BIPUSH,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(Arrays.stream(constructor.instructions.toArray())
                .filter(VarInsnNode.class::isInstance)
                .map(VarInsnNode.class::cast)
                .anyMatch(instruction ->
                        instruction.getOpcode() == Opcodes.ASTORE
                                && instruction.var == 0));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
        assertTrue(context.output.toString().contains(
                "env->SetIntField(obj"));
    }

    @Test
    public void admitsPrefixOnlyTryCatchAndRetainsItInRewrittenConstructor() {
        ClassNode owner = constructorOwner(
                "example/PrefixCatch", "example/PrefixCatchBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                prefixOnlyTryCatchConstructor(owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(Ljava/lang/String;I)V", nativeBody.desc);
        assertTrue(nativeBody.tryCatchBlocks.isEmpty());
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(1, constructor.tryCatchBlocks.size());
        TryCatchBlockNode retained = constructor.tryCatchBlocks.get(0);
        assertEquals("java/lang/NumberFormatException", retained.type);
        assertTrue(constructor.instructions.indexOf(retained.start) >= 0);
        assertTrue(constructor.instructions.indexOf(retained.end) >= 0);
        assertTrue(constructor.instructions.indexOf(retained.handler) >= 0);
        assertTrue(constructor.instructions.indexOf(retained.handler)
                < constructor.instructions.indexOf(
                retainedThisOrSuperCall(constructor, owner)));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(
                "(Ljava/lang/Object;Ljava/lang/String;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixReturnHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixReturnHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.POP, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.POP));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixGotoReturnHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedGotoCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixGotoReturnHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.POP, Opcodes.GOTO, Opcodes.RETURN),
                realOpcodes(nativeBody));
        JumpInsnNode relocatedGoto = Arrays.stream(
                        nativeBody.instructions.toArray())
                .filter(JumpInsnNode.class::isInstance)
                .map(JumpInsnNode.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(nativeBody.instructions.indexOf(relocatedGoto.label) >= 0);
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.POP));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixAstoreReturnHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedAstoreCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAstoreReturnHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ASTORE, Opcodes.RETURN),
                realOpcodes(nativeBody));
        VarInsnNode relocatedStore = Arrays.stream(
                        nativeBody.instructions.toArray())
                .filter(VarInsnNode.class::isInstance)
                .map(VarInsnNode.class::cast)
                .filter(instruction ->
                        instruction.getOpcode() == Opcodes.ASTORE)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, relocatedStore.var);
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ASTORE));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixAstoreGotoReturnHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedAstoreGotoCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAstoreGotoReturnHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ASTORE, Opcodes.GOTO, Opcodes.RETURN),
                realOpcodes(nativeBody));
        JumpInsnNode relocatedGoto = Arrays.stream(
                        nativeBody.instructions.toArray())
                .filter(JumpInsnNode.class::isInstance)
                .map(JumpInsnNode.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(nativeBody.instructions.indexOf(relocatedGoto.label) >= 0);
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ASTORE));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixAthrowHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedAthrowCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAthrowHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN, Opcodes.ATHROW),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ATHROW));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixTryCatchWithIsolatedPrefixAstoreAthrowHandler() {
        ClassNode owner = constructorOwner(
                "example/RelocatedAstoreAthrowCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAstoreAthrowHandler(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals(Arrays.asList(
                        Opcodes.BIPUSH, Opcodes.ILOAD, Opcodes.IDIV,
                        Opcodes.ISTORE, Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ASTORE, Opcodes.ALOAD, Opcodes.ATHROW),
                realOpcodes(nativeBody));
        java.util.List<VarInsnNode> relocatedExceptionLocalAccesses =
                Arrays.stream(nativeBody.instructions.toArray())
                        .filter(VarInsnNode.class::isInstance)
                        .map(VarInsnNode.class::cast)
                        .filter(instruction ->
                                instruction.getOpcode() == Opcodes.ASTORE
                                        || (instruction.getOpcode()
                                        == Opcodes.ALOAD
                                        && instruction.var == 3))
                        .collect(Collectors.toList());
        assertEquals(2, relocatedExceptionLocalAccesses.size());
        assertTrue(relocatedExceptionLocalAccesses.stream()
                .allMatch(instruction -> instruction.var == 3));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ASTORE));
        assertFalse(realOpcodes(constructor).contains(Opcodes.ATHROW));
        assertEquals(1, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsSuffixOnlyTryCatchInNativeBody() {
        ClassNode owner = constructorOwner(
                "example/SuffixCatch", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor = suffixOnlyTryCatchConstructor(owner.name);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals(1, nativeBody.tryCatchBlocks.size());
        assertEquals("java/lang/ArithmeticException",
                nativeBody.tryCatchBlocks.get(0).type);
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(context.output.toString().contains("goto IR_CATCH_"));
    }

    @Test
    public void admitsMultipleSuperDiamondWithSharedSuffix() {
        ClassNode owner = constructorOwner(
                "example/MultiSuper", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        MethodNode constructor = multipleSuperDiamondConstructor(
                owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(context.output.toString().contains(
                "env->SetIntField(obj"));
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(realOpcodes(constructor).contains(Opcodes.IFLT));
        assertTrue(realOpcodes(constructor).contains(Opcodes.GOTO));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsPostChainConditionalBranchToSharedSuffix() {
        ClassNode owner = constructorOwner(
                "example/PostChainBranch", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor = postChainConditionalBranchConstructor(
                owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(II)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.BIPUSH,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(realOpcodes(constructor).contains(Opcodes.IFNE));
        assertFalse(realOpcodes(constructor).contains(Opcodes.GOTO));
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsPostChainIntCompareFamiliesToSharedSuffix() {
        for (int opcode : POST_CHAIN_INT_COMPARE_OPCODES) {
            String ownerName = "example/PostChainCompare" + opcode;
            ClassNode owner = constructorOwner(
                    ownerName, "example/MultiSuperBase");
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            MethodNode constructor = postChainIntCompareConstructor(
                    owner.name, owner.superName, opcode);

            MethodNode nativeBody =
                    ConstructorSpecialMethodProcessor.createNativeBody(
                            owner, constructor);
            assertEquals(isBinaryIntCompare(opcode) ? "(III)V" : "(II)V",
                    nativeBody.desc, "opcode " + opcode);
            assertEquals(Arrays.asList(
                            Opcodes.ALOAD, Opcodes.BIPUSH,
                            Opcodes.PUTFIELD, Opcodes.RETURN),
                    realOpcodes(nativeBody), "opcode " + opcode);
            frontend.build(owner.name, nativeBody);

            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                    .processMethod(context);

            assertEquals(2, directChainCallCount(constructor, owner),
                    "opcode " + opcode);
            assertEquals(1, hiddenBridgeCallCount(constructor),
                    "opcode " + opcode);
            assertTrue(realOpcodes(constructor).contains(opcode),
                    "opcode " + opcode);
            assertFalse(realOpcodes(constructor).contains(Opcodes.GOTO),
                    "opcode " + opcode);
            assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD),
                    "opcode " + opcode);
            assertEquals(2, Collections.frequency(
                            realOpcodes(constructor), Opcodes.RETURN),
                    "opcode " + opcode);
        }
    }

    @Test
    public void admitsPostChainSwitchesToSharedSuffix() {
        for (boolean lookup : new boolean[]{false, true}) {
            String kind = lookup ? "Lookup" : "Table";
            ClassNode owner = constructorOwner(
                    "example/PostChain" + kind + "Switch",
                    "example/MultiSuperBase");
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            MethodNode constructor = postChainSwitchConstructor(
                    owner.name, owner.superName, lookup);

            MethodNode nativeBody =
                    ConstructorSpecialMethodProcessor.createNativeBody(
                            owner, constructor);
            assertEquals("(II)V", nativeBody.desc, kind);
            assertEquals(Arrays.asList(
                            Opcodes.ALOAD, Opcodes.BIPUSH,
                            Opcodes.PUTFIELD, Opcodes.RETURN),
                    realOpcodes(nativeBody), kind);
            frontend.build(owner.name, nativeBody);

            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                    .processMethod(context);

            assertTrue(constructor.tryCatchBlocks.isEmpty(), kind);
            assertEquals(2, directChainCallCount(constructor, owner), kind);
            assertEquals(1, hiddenBridgeCallCount(constructor), kind);
            assertTrue(realOpcodes(constructor).contains(
                    lookup ? Opcodes.LOOKUPSWITCH : Opcodes.TABLESWITCH), kind);
            assertTrue(realOpcodes(constructor).contains(Opcodes.GOTO), kind);
            assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD), kind);
            assertEquals(2, Collections.frequency(
                    realOpcodes(constructor), Opcodes.RETURN), kind);
            assertEquals(
                    "(Ljava/lang/Object;II)V",
                    context.proxyMethod.getMethodNode().desc, kind);
        }
    }

    @Test
    public void admitsConditionallyAssignedExtraOnBridgePathOnly() {
        ClassNode owner = constructorOwner(
                "example/ConditionalBridgeExtra", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode constructor = conditionalPrefixExitExtraConstructor(
                owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(ILjava/lang/Object;)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.ALOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
        assertEquals(
                "(Ljava/lang/Object;ILjava/lang/Object;)V",
                context.proxyMethod.getMethodNode().desc);
        MethodInsnNode bridge = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(bridge.getPrevious() instanceof VarInsnNode);
        assertEquals(Opcodes.ALOAD, bridge.getPrevious().getOpcode());
        assertEquals(2, ((VarInsnNode) bridge.getPrevious()).var);
    }

    @Test
    public void admitsMultipleSuperWithIdenticalLinearSuffixCopies() {
        ClassNode owner = constructorOwner(
                "example/MultiSuperCopies", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        MethodNode constructor =
                multipleSuperIdenticalSuffixCopiesConstructor(
                        owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ALOAD, Opcodes.ILOAD,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(context.output.toString().contains(
                "env->SetIntField(obj"));
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(realOpcodes(constructor).contains(Opcodes.GOTO));
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsMultipleSuperWithImmediateSeparateReturns() {
        ClassNode owner = constructorOwner(
                "example/MultiReturn", "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperSeparateReturnsConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertTrue(realOpcodes(constructor).contains(Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeSuperCallsWithImmediateSeparateReturns() {
        ClassNode owner = constructorOwner(
                "example/ThreeMultiReturn", "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeDeclaredArgumentReturnsConstructor(
                        owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(IIII)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;IIII)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeSuperCallsWithIdenticalNonemptyLinearSuffixCopies() {
        ClassNode owner = constructorOwner(
                "example/ThreeMultiSuffix", "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeIdenticalSuffixCopiesConstructor(
                        owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ICONST_4, Opcodes.POP, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertFalse(realOpcodes(constructor).contains(Opcodes.POP));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void rejectsPrefixBranchTargetingSuffixLabel() {
        ClassNode owner = constructorOwner("example/Skip", "java/lang/Object");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, prefixBranchTargetingSuffixConstructor()));
        assertEquals(Opcodes.IFNE, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor prefix branches across the this/super call"));
    }

    @Test
    public void rejectsUnprovenPostChainSwitchShapes() {
        for (boolean lookup : new boolean[]{false, true}) {
            String kind = lookup ? "lookup" : "table";
            ClassNode owner = constructorOwner(
                    "example/RejectedPostChainSwitch" + kind,
                    "example/MultiSuperBase");

            for (int variant = 0; variant < 3; variant++) {
                MethodNode constructor = postChainSwitchConstructor(
                        owner.name, owner.superName, lookup);
                if (variant == 0) {
                    addComputedPostChainSwitchKey(constructor);
                } else if (variant == 1) {
                    addWorkBeforePostChainSwitchReturn(constructor);
                } else {
                    addPostChainSwitchExceptionTable(constructor);
                }

                UnsupportedIrConstructException error = assertThrows(
                        UnsupportedIrConstructException.class,
                        () -> ConstructorSpecialMethodProcessor.createNativeBody(
                                owner, constructor),
                        kind + " variant " + variant);
                assertTrue(error.getMessage().contains(
                                "Constructor chain calls do not share one suffix join"),
                        kind + " variant " + variant);
            }
        }
    }

    @Test
    public void rejectsSwitchPathThatSkipsEveryChainCall() {
        for (boolean lookup : new boolean[]{false, true}) {
            String kind = lookup ? "lookup" : "table";
            ClassNode owner = constructorOwner(
                    "example/SkipChainSwitch" + kind, "java/lang/Object");
            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> ConstructorSpecialMethodProcessor.createNativeBody(
                            owner, switchSkippingChainConstructor(lookup)),
                    kind);
            assertEquals(
                    lookup ? Opcodes.LOOKUPSWITCH : Opcodes.TABLESWITCH,
                    error.getOpcode(), kind);
            assertTrue(error.getMessage().contains(
                    "Constructor prefix branches across the this/super call"),
                    kind);
        }
    }

    @Test
    public void rejectsSuffixJumpIntoConstructorPrefix() {
        ClassNode owner = constructorOwner("example/Loop", "java/lang/Object");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, suffixJumpIntoPrefixConstructor()));
        assertEquals(Opcodes.GOTO, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor suffix jumps into its bytecode prefix"));
    }

    @Test
    public void rejectsTryCatchCrossingConstructorSplit() {
        ClassNode owner = constructorOwner("example/Guarded", "java/lang/Object");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, tryCatchCrossingConstructor()));
        assertTrue(error.getMessage().contains(
                "Constructor exception regions may not cross the this/super split"));
    }

    @Test
    public void rejectsEveryMixedTryCatchLabelPlacement() {
        ClassNode owner =
                constructorOwner("example/MixedCatch", "java/lang/Object");
        for (int prefixMask = 1; prefixMask < 7; prefixMask++) {
            final int placement = prefixMask;
            MethodNode constructor =
                    mixedTryCatchLabelsConstructor(placement);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    "mixed try/catch label mask " + placement);
            assertTrue(error.getMessage().contains(
                    "Constructor exception regions may not cross "
                            + "the this/super split"));
            assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
            assertEquals(opcodes, realOpcodes(constructor));
            assertEquals(1, constructor.tryCatchBlocks.size());
            assertTrue(context.proxyMethod == null);
            assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
        }
    }

    @Test
    public void rejectsUnsafePrefixGotoReturnHandlersBeforeMutation() {
        String[] shapes = {
                "extra-work", "non-return-target", "extra-return-incoming"
        };
        for (String shape : shapes) {
            ClassNode owner = constructorOwner(
                    "example/RejectedGotoCatch" + shape.replace("-", ""),
                    "java/lang/Object");
            MethodNode constructor =
                    invalidPrefixGotoReturnHandler(owner.name, shape);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            int instructionCount = constructor.instructions.size();

            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertTrue(error.getMessage().contains(
                    "Constructor exception regions may not cross "
                            + "the this/super split"), shape);
            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertEquals(1, constructor.tryCatchBlocks.size(), shape);
            assertTrue(context.proxyMethod == null, shape);
            assertTrue(obfuscator.getHiddenMethodsPool()
                    .getClasses().isEmpty(), shape);
        }
    }

    @Test
    public void rejectsUnsafePrefixAstoreReturnHandlersBeforeMutation() {
        String[] shapes = {
                "astore-0", "extra-work", "stored-exception-use",
                "non-return-target", "extra-return-incoming",
                "category-2-hole"
        };
        for (String shape : shapes) {
            ClassNode owner = constructorOwner(
                    "example/RejectedAstoreCatch" + shape.replace("-", ""),
                    "java/lang/Object");
            MethodNode constructor =
                    invalidPrefixAstoreReturnHandler(owner.name, shape);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            int instructionCount = constructor.instructions.size();

            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertTrue(error.getMessage().contains(
                    "Constructor exception regions may not cross "
                            + "the this/super split"), shape);
            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertEquals(1, constructor.tryCatchBlocks.size(), shape);
            assertTrue(context.proxyMethod == null, shape);
            assertTrue(obfuscator.getHiddenMethodsPool()
                    .getClasses().isEmpty(), shape);
        }
    }

    @Test
    public void rejectsUnsafePrefixAthrowHandlersBeforeMutation() {
        String[] shapes = {
                "astore-0", "pop-athrow", "extra-work",
                "astore-without-reload", "stored-exception-use"
        };
        for (String shape : shapes) {
            ClassNode owner = constructorOwner(
                    "example/RejectedAthrowCatch" + shape.replace("-", ""),
                    "java/lang/Object");
            MethodNode constructor =
                    invalidPrefixAthrowHandler(owner.name, shape);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            int instructionCount = constructor.instructions.size();

            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertTrue(error.getMessage().contains(
                    "Constructor exception regions may not cross "
                            + "the this/super split"), shape);
            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertEquals(1, constructor.tryCatchBlocks.size(), shape);
            assertTrue(context.proxyMethod == null, shape);
            assertTrue(obfuscator.getHiddenMethodsPool()
                    .getClasses().isEmpty(), shape);
        }
    }

    @Test
    public void rejectsPathThatExecutesTwoSuperCalls() {
        ClassNode owner = constructorOwner("example/Twice", "java/lang/Object");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, multipleSuperCallConstructor()));
        assertEquals(Opcodes.INVOKESPECIAL, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor path can execute multiple this/super calls"));
    }

    @Test
    public void rejectsMultipleSuperDiamondWithPrefixAstoreZero() {
        ClassNode owner = constructorOwner(
                "example/MultiReceiverStore", "example/MultiSuperBase");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, multipleSuperDiamondWithAstoreZero(
                                owner.name, owner.superName)));
        assertEquals(Opcodes.ASTORE, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor prefix ASTORE 0 does not provably preserve "
                        + "the constructor receiver"));
    }

    @Test
    public void rejectsMultipleSuperPathThatSkipsEveryChainCall() {
        ClassNode owner = constructorOwner(
                "example/MultiSkip", "example/MultiSuperBase");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, multipleSuperDiamondWithSkipPath(
                                owner.name, owner.superName)));
        assertEquals(Opcodes.IFEQ, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor prefix branches across the this/super call"));
    }

    @Test
    public void rejectsTryCatchCoveringMultipleSuperChainAndSuffix() {
        ClassNode owner = constructorOwner(
                "example/MultiTry", "example/MultiSuperBase");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, multipleSuperDiamondWithCrossingTryCatch(
                                owner.name, owner.superName)));
        assertTrue(error.getMessage().contains(
                "Constructor exception regions may not cross the this/super split"));
    }

    @Test
    public void admitsTwoSuperCallsWithDifferentStraightLineSuffixes() {
        ClassNode owner = constructorOwner(
                "example/MultiSuffix", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                multipleSuperDifferentSuffixConstructor(
                        owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(II)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ILOAD, Opcodes.IFNE,
                        Opcodes.ALOAD, Opcodes.ICONST_0,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ALOAD, Opcodes.ICONST_1,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(2, hiddenBridgeCallCount(constructor));
        assertEquals(1, Arrays.stream(constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .map(invoke -> invoke.owner + "." + invoke.name + invoke.desc)
                .distinct().count());
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
    }

    @Test
    public void admitsTwoDistinctSuffixesWithProvenPrefixExtra() {
        ClassNode owner = constructorOwner(
                "example/TwoSuffixExtra", "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                twoDistinctSuffixesWithProvenPrefixExtraConstructor(
                        owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(III)V", nativeBody.desc);
        assertEquals(Arrays.asList(3, 2, 2),
                variableIndexes(nativeBody, Opcodes.ILOAD));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(2, hiddenBridgeCallCount(constructor));
        assertEquals(
                "(Ljava/lang/Object;III)V",
                context.proxyMethod.getMethodNode().desc);
        java.util.List<MethodInsnNode> bridges = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .collect(Collectors.toList());
        assertEquals(2, bridges.size());
        assertEquals(1L, bridges.stream()
                .map(invoke -> invoke.owner + "." + invoke.name + invoke.desc)
                .distinct().count());
        for (MethodInsnNode bridge : bridges) {
            assertTrue(bridge.getPrevious().getOpcode() >= Opcodes.ICONST_0
                    && bridge.getPrevious().getOpcode() <= Opcodes.ICONST_1);
            assertEquals(Opcodes.ILOAD,
                    bridge.getPrevious().getPrevious().getOpcode());
            assertEquals(2, ((VarInsnNode)
                    bridge.getPrevious().getPrevious()).var);
        }
    }

    @Test
    public void admitsThreeSuperCallsWithDistinctStraightLineSuffixes() {
        ClassNode owner = constructorOwner(
                "example/ThreeDistinctSuffixes",
                "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                multipleSuperThreeDistinctSuffixConstructor(
                        owner.name, owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(II)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ILOAD, Opcodes.TABLESWITCH,
                        Opcodes.ACONST_NULL, Opcodes.ATHROW,
                        Opcodes.ALOAD, Opcodes.ICONST_3,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ALOAD, Opcodes.ICONST_4,
                        Opcodes.PUTFIELD, Opcodes.RETURN,
                        Opcodes.ALOAD, Opcodes.ICONST_5,
                        Opcodes.PUTFIELD, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(3, hiddenBridgeCallCount(constructor));
        assertEquals(1, Arrays.stream(constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .map(invoke -> invoke.owner + "." + invoke.name + invoke.desc)
                .distinct().count());
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
        assertFalse(realOpcodes(constructor).contains(Opcodes.PUTFIELD));
    }

    @Test
    public void admitsFourSuperCallsWithPairwiseDistinctStraightLineSuffixes() {
        ClassNode owner = constructorOwner(
                "example/FourDistinctSuffixes",
                "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperFourDistinctSuffixConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(II)V", nativeBody.desc);
        assertEquals(Arrays.asList(
                        Opcodes.ILOAD, Opcodes.TABLESWITCH,
                        Opcodes.ACONST_NULL, Opcodes.ATHROW,
                        Opcodes.ICONST_2, Opcodes.POP, Opcodes.RETURN,
                        Opcodes.ICONST_3, Opcodes.POP, Opcodes.RETURN,
                        Opcodes.ICONST_4, Opcodes.POP, Opcodes.RETURN,
                        Opcodes.ICONST_5, Opcodes.POP, Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(4, directChainCallCount(constructor, owner));
        assertEquals(4, hiddenBridgeCallCount(constructor));
        assertEquals(1, Arrays.stream(constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .map(invoke -> invoke.owner + "." + invoke.name + invoke.desc)
                .distinct().count());
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeImmediateReturnsWithIaddOfProvenChainInputs() {
        ClassNode owner = constructorOwner(
                "example/ThreeComputedMultiReturn",
                "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeSeparateReturnsConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IADD));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeImmediateReturnsWithIsubAndImulOfProvenChainInputs() {
        ClassNode owner = constructorOwner(
                "example/ThreeArithmeticMultiReturn",
                "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeIsubImulReturnsConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISUB));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IMUL));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeImmediateReturnsWithBitwiseProvenChainInputs() {
        ClassNode owner = constructorOwner(
                "example/ThreeBitwiseMultiReturn",
                "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeBitwiseReturnsConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IAND));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IOR));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IXOR));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void admitsThreeImmediateReturnsWithShiftProvenChainInputs() {
        ClassNode owner = constructorOwner(
                "example/ThreeShiftMultiReturn",
                "example/MultiSuperBase");
        MethodNode constructor =
                multipleSuperThreeShiftReturnsConstructor(owner.superName);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(I)V", nativeBody.desc);
        assertEquals(Collections.singletonList(Opcodes.RETURN),
                realOpcodes(nativeBody));
        frontend.build(owner.name, nativeBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISHL));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISHR));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IUSHR));
        assertEquals(2, Collections.frequency(
                realOpcodes(constructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.RETURN));
        assertEquals(
                "(Ljava/lang/Object;I)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void rejectsUnboundedThreeImmediateReturnShapesBeforeMutation() {
        for (String shape : Arrays.asList(
                "extra-local", "iadd-extra-local", "nested-iadd",
                "isub-extra-local", "imul-extra-local",
                "iand-extra-local", "ior-extra-local",
                "ixor-extra-local", "ishl-extra-local",
                "ishr-extra-local", "iushr-extra-local",
                "nested-isub", "nested-imul",
                "nested-iand", "nested-ior", "nested-ixor",
                "nested-ishl", "nested-ishr", "nested-iushr", "idiv",
                "astore-zero", "post-call", "skip-super",
                "exception-table")) {
            ClassNode owner = constructorOwner(
                    "example/RejectedThree"
                            + shape.replace("-", ""),
                    "example/MultiSuperBase");
            MethodNode constructor =
                    rejectedThreeImmediateReturnsConstructor(
                            owner.superName, shape);
            int instructionCount = constructor.instructions.size();
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(
                            obfuscator, constructor, 0, owner, 0);

            assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertTrue(context.proxyMethod == null, shape);
            assertTrue(obfuscator.getHiddenMethodsPool()
                    .getClasses().isEmpty(), shape);
        }
    }

    @Test
    public void rejectsUnprovenThreeDistinctSuffixShapesBeforeMutation() {
        for (String shape : Arrays.asList(
                "partly-identical", "branch", "skip-super",
                "exception-table", "extra-local-suffix")) {
            ClassNode owner = constructorOwner(
                    "example/RejectedThreeSuffix"
                            + shape.replace("-", ""),
                    "example/MultiSuperBase");
            MethodNode constructor =
                    rejectedThreeNonemptySuffixCopiesConstructor(
                            owner.superName, shape);
            int instructionCount = constructor.instructions.size();
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(
                            obfuscator, constructor, 0, owner, 0);

            assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertTrue(context.proxyMethod == null, shape);
            assertTrue(obfuscator.getHiddenMethodsPool()
                    .getClasses().isEmpty(), shape);
        }
    }

    @Test
    public void rejectsUnprovenTwoDifferentSuffixShapesBeforeMutation() {
        for (String shape :
                Arrays.asList("branch", "exception-table")) {
            ClassNode owner = constructorOwner(
                    "example/RejectedTwoSuffix"
                            + shape.replace("-", ""),
                    "example/MultiSuperBase");
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            MethodNode constructor =
                    rejectedTwoDifferentSuffixConstructor(
                            owner.name, owner.superName, shape);
            int instructionCount = constructor.instructions.size();
            java.util.List<Integer> opcodes = realOpcodes(constructor);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(
                            obfuscator, constructor, 0, owner, 0);

            assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(
                            new MethodShellEmitter(obfuscator))
                            .processMethod(context),
                    shape);

            assertUnchangedAfterRejectedIr(
                    constructor, context, obfuscator);
            assertEquals(instructionCount,
                    constructor.instructions.size(), shape);
            assertEquals(opcodes, realOpcodes(constructor), shape);
            assertTrue(context.proxyMethod == null, shape);
        }
    }

    @Test
    public void rejectsUnassignedExtraOnDistinctSuffixBridgePathBeforeMutation() {
        ClassNode owner = constructorOwner(
                "example/RejectedUnassignedTwoSuffixExtra",
                "example/MultiSuperBase");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                twoDistinctSuffixesWithUnassignedBridgeExtraConstructor(
                        owner.name, owner.superName);
        int instructionCount = constructor.instructions.size();
        java.util.List<Integer> opcodes = realOpcodes(constructor);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertTrue(error.getMessage().contains(
                "is not definitely assigned on every path reaching "
                        + "a distinct-suffix bridge"));
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
    }

    @Test
    public void rejectsUnsupportedConstructorBeforeAnyMutation() {
        MethodNode constructor = unsupportedConstructor();
        ClassNode owner = constructorOwner("example/Unsupported", "java/lang/Object");
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        int instructionCount = constructor.instructions.size();
        java.util.List<Integer> opcodes = realOpcodes(constructor);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
    }

    @Test
    public void admitsPrefixWritesToForwardedReferenceParameters()
            throws Exception {
        MethodNode constructor = referenceParameterReassignedBeforeSuperConstructor();
        ClassNode owner = constructorOwner(
                "example/ParameterPrefix", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.methods.add(constructor);

        MethodNode nativeBody =
                ConstructorSpecialMethodProcessor.createNativeBody(owner, constructor);
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;)V", nativeBody.desc);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                context.proxyMethod.getMethodNode().desc);
        assertTrue(retainsBridgeInvocation(
                constructor, context.proxyMethod.getMethodNode().name));

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> parameterClass = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> parameterClass.getConstructor(String.class, Object.class)
                        .newInstance("value", new Object()));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
    }

    @Test
    public void forwardsPrefixExtraReferenceAndIntLocalsIntoSuffixDescriptors() {
        ClassNode referenceOwner = constructorOwner(
                "example/ReferenceExtra", "java/lang/Object");
        referenceOwner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode referenceConstructor =
                prefixExtraReferenceConstructor(referenceOwner.name);
        MethodNode referenceBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        referenceOwner, referenceConstructor);
        assertEquals("(Ljava/lang/String;Ljava/lang/Object;)V",
                referenceBody.desc);
        assertEquals(Arrays.asList(0, 2),
                variableIndexes(referenceBody, Opcodes.ALOAD));
        frontend.build(referenceOwner.name, referenceBody);

        NativeObfuscator referenceObfuscator = new NativeObfuscator();
        MethodContext referenceContext = new MethodContext(
                referenceObfuscator, referenceConstructor, 0, referenceOwner, 0);
        new IrMethodCompiler(new MethodShellEmitter(referenceObfuscator))
                .processMethod(referenceContext);
        assertEquals(
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V",
                referenceContext.proxyMethod.getMethodNode().desc);
        assertTrue(referenceContext.output.toString().contains(
                "jobject ignored_hidden, jobject obj, "
                        + "jobject arg0, jobject arg1"));

        ClassNode intOwner = constructorOwner(
                "example/IntExtra", "java/lang/Object");
        intOwner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode intConstructor = prefixExtraIntConstructor(intOwner.name);
        MethodNode intBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        intOwner, intConstructor);
        assertEquals("(II)V", intBody.desc);
        assertEquals(Collections.singletonList(2),
                variableIndexes(intBody, Opcodes.ILOAD));
        frontend.build(intOwner.name, intBody);
    }

    @Test
    public void packsAndRemapsGappedPrefixExtrasInIndependentSuffix() {
        ClassNode owner = constructorOwner(
                "example/GappedExtra", "java/lang/Object");
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode constructor =
                gappedPrefixExtraReferenceConstructor(owner.name);

        MethodNode body =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(Ljava/lang/String;Ljava/lang/Object;)V", body.desc);
        assertEquals(Arrays.asList(0, 2),
                variableIndexes(body, Opcodes.ALOAD));
        assertFalse(variableIndexes(body, Opcodes.ALOAD).contains(3));
        frontend.build(owner.name, body);

        ClassNode incrementOwner = constructorOwner(
                "example/GappedIncrement", "java/lang/Object");
        incrementOwner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode incrementBody =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        incrementOwner,
                        gappedPrefixExtraIincConstructor(incrementOwner.name));
        assertEquals("(II)V", incrementBody.desc);
        assertEquals(Collections.singletonList(2),
                variableIndexes(incrementBody, Opcodes.ILOAD));
        IincInsnNode increment = Arrays.stream(
                        incrementBody.instructions.toArray())
                .filter(IincInsnNode.class::isInstance)
                .map(IincInsnNode.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, increment.var);
        frontend.build(incrementOwner.name, incrementBody);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);
        assertEquals(
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V",
                context.proxyMethod.getMethodNode().desc);
        MethodInsnNode bridge = Arrays.stream(constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(bridge.getPrevious() instanceof VarInsnNode);
        assertEquals(Opcodes.ALOAD, bridge.getPrevious().getOpcode());
        assertEquals(3, ((VarInsnNode) bridge.getPrevious()).var);
    }

    @Test
    public void forwardsPrimitivePrefixExtrasWithExactTypesAndWideSlots() {
        ClassNode owner = constructorOwner(
                "example/PrimitiveExtras", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "intValue", "I", null, null));
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "longValue", "J", null, null));
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "floatValue", "F", null, null));
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "doubleValue", "D", null, null));
        MethodNode constructor =
                prefixExtraPrimitiveConstructor(owner.name);

        MethodNode body =
                ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, constructor);
        assertEquals("(IJFD)V", body.desc);
        assertEquals(7, body.maxLocals);
        frontend.build(owner.name, body);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);
        assertEquals("(Ljava/lang/Object;IJFD)V",
                context.proxyMethod.getMethodNode().desc);
        assertTrue(context.output.toString().contains(
                "jobject ignored_hidden, jobject obj, jint arg0, "
                        + "jlong arg1, jfloat arg2, jdouble arg3"));
    }

    @Test
    public void rejectsConditionallyAssignedPrefixExtraBeforeMutation() {
        ClassNode owner = constructorOwner(
                "example/ConditionalExtra", "java/lang/Object");
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "Ljava/lang/Object;", null, null));
        MethodNode constructor =
                conditionallyAssignedPrefixExtraConstructor(owner.name);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        int instructionCount = constructor.instructions.size();
        java.util.List<Integer> opcodes = realOpcodes(constructor);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.INVOKESPECIAL, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "prefix extra local 2 is not definitely assigned on every path"));
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
    }

    @Test
    public void rewrittenPrefixExtraLocalConstructorPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedExtra", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode constructor = prefixExtraReferenceConstructor(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(String.class)
                        .newInstance("verify-extra"));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
    }

    @Test
    public void rewrittenIdentityAstoreZeroConstructorPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedIdentityReceiver", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                identityAstoreZeroConstructor(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor().newInstance());
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenGappedPrefixExtraConstructorPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedGappedExtra", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode constructor =
                gappedPrefixExtraReferenceConstructor(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(String.class)
                        .newInstance("verify-gapped-extra"));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
    }

    @Test
    public void rewrittenMultipleSuperDiamondPassesJvmVerification()
            throws Exception {
        ClassNode base = multipleSuperBase("example/VerifiedMultiBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedMulti", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        MethodNode constructor =
                multipleSuperDiamondConstructor(owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(-7));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenPostChainConditionalBranchPassesJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedPostChainBranchBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedPostChainBranch", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor = postChainConditionalBranchConstructor(
                owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        Object earlyReturn = verified.getConstructor(int.class, int.class)
                .newInstance(7, 0);
        assertEquals(0, verified.getField("result").getInt(earlyReturn));
        for (int[] arguments : new int[][]{{7, 1}, {-5, 0}}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class, int.class)
                            .newInstance(arguments[0], arguments[1]));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenPostChainIntCompareFamiliesPassJvmVerification()
            throws Exception {
        for (int opcode : POST_CHAIN_INT_COMPARE_OPCODES) {
            String suffix = Integer.toString(opcode);
            ClassNode base = multipleSuperBase(
                    "example/VerifiedPostChainCompareBase" + suffix);
            base.version = Opcodes.V1_8;
            ClassNode owner = constructorOwner(
                    "example/VerifiedPostChainCompare" + suffix, base.name);
            owner.version = Opcodes.V1_8;
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            MethodNode constructor = postChainIntCompareConstructor(
                    owner.name, base.name, opcode);
            owner.methods.add(constructor);

            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                    .processMethod(context);

            ByteArrayClassLoader loader = new ByteArrayClassLoader();
            loader.define(writeClass(base));
            for (ClassNode hidden :
                    obfuscator.getHiddenMethodsPool().getClasses()) {
                loader.define(writeClass(hidden));
            }
            Class<?> verified = loader.define(writeClass(owner));
            int[][] operands = postChainCompareOperands(opcode);

            InvocationTargetException taken = assertThrows(
                    InvocationTargetException.class,
                    () -> newPostChainCompareInstance(
                            verified, opcode, 7, operands[0]),
                    "suffix-taken opcode " + opcode);
            assertTrue(taken.getCause() instanceof UnsatisfiedLinkError,
                    "suffix-taken opcode " + opcode);

            Object earlyReturn = newPostChainCompareInstance(
                    verified, opcode, 7, operands[1]);
            assertEquals(0, verified.getField("result").getInt(earlyReturn),
                    "immediate-return opcode " + opcode);

            InvocationTargetException secondCall = assertThrows(
                    InvocationTargetException.class,
                    () -> newPostChainCompareInstance(
                            verified, opcode, -5, operands[1]),
                    "second-call opcode " + opcode);
            assertTrue(secondCall.getCause() instanceof UnsatisfiedLinkError,
                    "second-call opcode " + opcode);
            assertEquals(2, directChainCallCount(constructor, owner),
                    "opcode " + opcode);
            assertEquals(1, hiddenBridgeCallCount(constructor),
                    "opcode " + opcode);
        }
    }

    @Test
    public void rewrittenPostChainSwitchesPassJvmVerification()
            throws Exception {
        for (boolean lookup : new boolean[]{false, true}) {
            String kind = lookup ? "Lookup" : "Table";
            ClassNode base = multipleSuperBase(
                    "example/VerifiedPostChain" + kind + "SwitchBase");
            base.version = Opcodes.V1_8;
            ClassNode owner = constructorOwner(
                    "example/VerifiedPostChain" + kind + "Switch", base.name);
            owner.version = Opcodes.V1_8;
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            MethodNode constructor = postChainSwitchConstructor(
                    owner.name, base.name, lookup);
            owner.methods.add(constructor);

            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context =
                    new MethodContext(obfuscator, constructor, 0, owner, 0);
            new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                    .processMethod(context);

            ByteArrayClassLoader loader = new ByteArrayClassLoader();
            loader.define(writeClass(base));
            for (ClassNode hidden :
                    obfuscator.getHiddenMethodsPool().getClasses()) {
                loader.define(writeClass(hidden));
            }
            Class<?> verified = loader.define(writeClass(owner));

            Object defaultReturn = verified.getConstructor(
                            int.class, int.class)
                    .newInstance(7, 99);
            assertEquals(0, verified.getField("result").getInt(defaultReturn),
                    kind + " default return");

            int[] suffixKeys = lookup
                    ? new int[]{7, 42} : new int[]{0, 1};
            for (int key : suffixKeys) {
                InvocationTargetException suffix = assertThrows(
                        InvocationTargetException.class,
                        () -> verified.getConstructor(int.class, int.class)
                                .newInstance(7, key),
                        kind + " suffix key " + key);
                assertTrue(suffix.getCause() instanceof UnsatisfiedLinkError,
                        kind + " suffix key " + key);
            }

            InvocationTargetException secondCall = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class, int.class)
                            .newInstance(-5, 99),
                    kind + " second call");
            assertTrue(secondCall.getCause() instanceof UnsatisfiedLinkError,
                    kind + " second call");
            assertEquals(2, directChainCallCount(constructor, owner), kind);
            assertEquals(1, hiddenBridgeCallCount(constructor), kind);
        }
    }

    @Test
    public void rewrittenConditionalBridgeExtraPassesJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedConditionalExtraBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedConditionalExtra", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        MethodNode constructor = conditionalPrefixExitExtraConstructor(
                owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        verified.getConstructor(int.class).newInstance(0);
        InvocationTargetException bridge = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(1));
        assertTrue(bridge.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenIdenticalMultiSuperSuffixCopiesPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedMultiCopiesBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedMultiCopies", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        MethodNode constructor =
                multipleSuperIdenticalSuffixCopiesConstructor(
                        owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int value : new int[]{7, -7}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(value));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenTwoDifferentSuffixesPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedTwoSuffixBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedTwoSuffix", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                multipleSuperDifferentSuffixConstructor(
                        owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int value : new int[]{7, -7}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(value));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(2, hiddenBridgeCallCount(constructor));
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void rewrittenTwoDistinctSuffixesWithExtraPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedTwoSuffixExtraBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedTwoSuffixExtra", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                twoDistinctSuffixesWithProvenPrefixExtraConstructor(
                        owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int value : new int[]{7, -7}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(value));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(2, hiddenBridgeCallCount(constructor));
        assertEquals(
                "(Ljava/lang/Object;III)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void rewrittenThreeDistinctSuffixesPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeSuffixBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeSuffix", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                multipleSuperThreeDistinctSuffixConstructor(
                        owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int value : new int[]{7, -7, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(value));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(3, hiddenBridgeCallCount(constructor));
        assertEquals(
                "(Ljava/lang/Object;II)V",
                context.proxyMethod.getMethodNode().desc);
    }

    @Test
    public void rewrittenImmediateMultiSuperReturnsPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedMultiReturnBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedMultiReturn", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperSeparateReturnsConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int value : new int[]{7, -7}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(value));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(2, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenThreeImmediateIaddSuperReturnsPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeMultiReturnBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeMultiReturn", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperThreeSeparateReturnsConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int argument : new int[]{7, -7, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(argument));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IADD));
    }

    @Test
    public void rewrittenThreeImmediateIsubImulSuperReturnsPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeArithmeticBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeArithmetic", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperThreeIsubImulReturnsConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int argument : new int[]{11, -22, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(argument));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISUB));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IMUL));
    }

    @Test
    public void rewrittenThreeImmediateBitwiseSuperReturnsPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeBitwiseBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeBitwise", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperThreeBitwiseReturnsConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int argument : new int[]{11, -22, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(argument));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IAND));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IOR));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IXOR));
    }

    @Test
    public void rewrittenThreeImmediateShiftSuperReturnsPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeShiftBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeShift", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperThreeShiftReturnsConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int argument : new int[]{11, -22, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(argument));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISHL));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.ISHR));
        assertEquals(1, Collections.frequency(
                realOpcodes(constructor), Opcodes.IUSHR));
    }

    @Test
    public void rewrittenThreeIdenticalNonemptySuffixCopiesPassJvmVerification()
            throws Exception {
        ClassNode base =
                multipleSuperBase("example/VerifiedThreeMultiSuffixBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedThreeMultiSuffix", base.name);
        owner.version = Opcodes.V1_8;
        MethodNode constructor =
                multipleSuperThreeIdenticalSuffixCopiesConstructor(base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        for (int argument : new int[]{7, -7, 0}) {
            InvocationTargetException error = assertThrows(
                    InvocationTargetException.class,
                    () -> verified.getConstructor(int.class)
                            .newInstance(argument));
            assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        }
        assertEquals(3, directChainCallCount(constructor, owner));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenPrefixOnlyTryCatchConstructorPassesJvmVerification()
            throws Exception {
        ClassNode base = multipleSuperBase("example/VerifiedPrefixCatchBase");
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(
                "example/VerifiedPrefixCatch", base.name);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                prefixOnlyTryCatchConstructor(owner.name, base.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        loader.define(writeClass(base));
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(String.class)
                        .newInstance("not-an-integer"));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(1, constructor.tryCatchBlocks.size());
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenRelocatedPrefixReturnHandlerPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedRelocatedCatch", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixReturnHandler(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        InvocationTargetException bridge = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(0));
        assertTrue(bridge.getCause() instanceof UnsatisfiedLinkError);
        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenRelocatedPrefixGotoReturnHandlerPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedRelocatedGotoCatch", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixGotoReturnHandler(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        InvocationTargetException bridge = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(0));
        assertTrue(bridge.getCause() instanceof UnsatisfiedLinkError);
        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.POP));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenRelocatedPrefixAstoreReturnHandlerPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedRelocatedAstoreCatch", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAstoreReturnHandler(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        InvocationTargetException bridge = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(0));
        assertTrue(bridge.getCause() instanceof UnsatisfiedLinkError);
        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ASTORE));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void rewrittenRelocatedPrefixAthrowHandlerPassesJvmVerification()
            throws Exception {
        ClassNode owner = constructorOwner(
                "example/VerifiedRelocatedAthrowCatch", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        MethodNode constructor =
                suffixTryCatchWithPrefixAthrowHandler(owner.name);
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));

        InvocationTargetException bridge = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(0));
        assertTrue(bridge.getCause() instanceof UnsatisfiedLinkError);
        assertTrue(constructor.tryCatchBlocks.isEmpty());
        assertFalse(realOpcodes(constructor).contains(Opcodes.ATHROW));
        assertEquals(1, hiddenBridgeCallCount(constructor));
    }

    @Test
    public void prefixExtraReferenceLocalCompilesAndRunsWithJavaParity()
            throws Exception {
        prefixExtraLocalCompilesAndRunsWithJavaParity(
                "ir-prefix-extra-run", "example/FlexCtorExtraLocal", false);
    }

    @Test
    public void gappedPrefixExtraReferenceLocalCompilesAndRunsWithJavaParity()
            throws Exception {
        prefixExtraLocalCompilesAndRunsWithJavaParity(
                "ir-gapped-prefix-extra-run",
                "example/FlexCtorGappedExtraLocal", true);
    }

    @Test
    public void conditionalBridgeExtraCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the conditional-extra runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the conditional-extra runtime test");

        String ownerName = "example/ConditionalBridgeExtraRuntime";
        String baseName = "example/ConditionalBridgeExtraRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-conditional-extra-run");
        Path inputJar = directory.resolve("conditional-extra.jar");
        Path outputDirectory = directory.resolve("output");
        createConditionalBridgeExtraJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain conditional-extra Java run");
        assertEquals(
                "BRIDGE-ASSIGNED" + System.lineSeparator()
                        + "PREFIX-EXIT" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        MethodInsnNode bridge = Arrays.stream(
                        transformedConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(
                "(Ljava/lang/Object;ILjava/lang/Object;)V",
                bridge.desc);

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("conditional-extra CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("conditional-extra CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Conditional-extra native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native conditional-extra Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void postChainConditionalBranchCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the post-chain branch runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the post-chain branch runtime test");

        String ownerName = "example/PostChainBranchRuntime";
        String baseName = "example/PostChainBranchRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-post-chain-branch-run");
        Path inputJar = directory.resolve("post-chain-branch.jar");
        Path outputDirectory = directory.resolve("output");
        createPostChainConditionalBranchJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain post-chain branch Java run");
        assertEquals(
                "7" + System.lineSeparator()
                        + "41" + System.lineSeparator()
                        + "7" + System.lineSeparator()
                        + "0" + System.lineSeparator()
                        + "5" + System.lineSeparator()
                        + "41" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertTrue(realOpcodes(transformedConstructor).contains(Opcodes.IFNE));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("post-chain branch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("post-chain branch CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Post-chain branch native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native post-chain branch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void postChainIntCompareFamiliesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the post-chain compare runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the post-chain compare runtime test");

        String ownerPrefix = "example/PostChainCompareRuntime";
        String baseName = ownerPrefix + "Base";
        String mainName = ownerPrefix + "Main";
        Path directory =
                Files.createTempDirectory("ir-post-chain-compare-run");
        Path inputJar = directory.resolve("post-chain-compares.jar");
        Path outputDirectory = directory.resolve("output");
        createPostChainIntCompareFamilyJar(
                inputJar, ownerPrefix, baseName, mainName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain post-chain compare Java run");
        StringBuilder expected = new StringBuilder();
        for (int ignored : POST_CHAIN_INT_COMPARE_OPCODES) {
            expected.append(41).append(System.lineSeparator());
            expected.append(0).append(System.lineSeparator());
            expected.append(41).append(System.lineSeparator());
        }
        assertEquals(expected.toString(), javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        mainName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            for (int opcode : POST_CHAIN_INT_COMPARE_OPCODES) {
                String ownerName = ownerPrefix + opcode;
                ClassNode transformed = new ClassNode(Opcodes.ASM9);
                new org.objectweb.asm.ClassReader(jar.getInputStream(
                        jar.getJarEntry(ownerName + ".class")))
                        .accept(transformed, 0);
                MethodNode transformedConstructor = transformed.methods.stream()
                        .filter(method -> "<init>".equals(method.name))
                        .findFirst().orElseThrow(AssertionError::new);
                assertEquals(2, directChainCallCount(
                                transformedConstructor, transformed),
                        "opcode " + opcode);
                assertEquals(1, hiddenBridgeCallCount(transformedConstructor),
                        "opcode " + opcode);
                assertTrue(realOpcodes(transformedConstructor).contains(opcode),
                        "opcode " + opcode);
            }
        }

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("post-chain compare CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("post-chain compare CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Post-chain compare native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native post-chain compare Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void postChainSwitchesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the post-chain switch runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the post-chain switch runtime test");

        String ownerPrefix = "example/PostChainSwitchRuntime";
        String tableOwner = ownerPrefix + "Table";
        String lookupOwner = ownerPrefix + "Lookup";
        String baseName = ownerPrefix + "Base";
        String mainName = ownerPrefix + "Main";
        Path directory =
                Files.createTempDirectory("ir-post-chain-switch-run");
        Path inputJar = directory.resolve("post-chain-switches.jar");
        Path outputDirectory = directory.resolve("output");
        createPostChainSwitchJar(
                inputJar, tableOwner, lookupOwner, baseName, mainName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain post-chain switch Java run");
        assertEquals(
                "41" + System.lineSeparator()
                        + "41" + System.lineSeparator()
                        + "0" + System.lineSeparator()
                        + "41" + System.lineSeparator()
                        + "41" + System.lineSeparator()
                        + "41" + System.lineSeparator()
                        + "0" + System.lineSeparator()
                        + "41" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        mainName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            for (boolean lookup : new boolean[]{false, true}) {
                String ownerName = lookup ? lookupOwner : tableOwner;
                ClassNode transformed = new ClassNode(Opcodes.ASM9);
                new org.objectweb.asm.ClassReader(jar.getInputStream(
                        jar.getJarEntry(ownerName + ".class")))
                        .accept(transformed, 0);
                MethodNode transformedConstructor = transformed.methods.stream()
                        .filter(method -> "<init>".equals(method.name))
                        .findFirst().orElseThrow(AssertionError::new);
                assertEquals(2, directChainCallCount(
                        transformedConstructor, transformed), ownerName);
                assertEquals(1, hiddenBridgeCallCount(transformedConstructor),
                        ownerName);
                assertTrue(realOpcodes(transformedConstructor).contains(
                        lookup ? Opcodes.LOOKUPSWITCH : Opcodes.TABLESWITCH),
                        ownerName);
            }
        }

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("post-chain switch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("post-chain switch CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Post-chain switch native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native post-chain switch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void multipleSuperDiamondCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the multi-super runtime test");

        String ownerName = "example/MultiSuperRuntime";
        String baseName = "example/MultiSuperRuntimeBase";
        Path directory = Files.createTempDirectory("ir-multi-super-run");
        Path inputJar = directory.resolve("multi-super.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperDiamondJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain multi-super Java run");
        assertEquals(
                "7" + System.lineSeparator()
                        + "5" + System.lineSeparator()
                        + "-5" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("multi-super CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Multi-super native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void twoDifferentSuffixesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the two-suffix runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the two-suffix runtime test");

        String ownerName = "example/TwoSuffixRuntime";
        String baseName = "example/TwoSuffixRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-two-suffix-run");
        Path inputJar = directory.resolve("two-suffix.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperDifferentSuffixJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain two-suffix multi-super Java run");
        assertEquals(
                "0" + System.lineSeparator()
                        + "1" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(2, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1L,
                Arrays.stream(transformedConstructor.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .filter(invoke ->
                                invoke.getOpcode() == Opcodes.INVOKESTATIC
                                        && invoke.owner.contains("/hidden/"))
                        .map(invoke -> invoke.owner + "." + invoke.name
                                + invoke.desc)
                        .distinct().count());

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList(
                                "cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("two-suffix multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("two-suffix multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Two-suffix native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native two-suffix multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void twoDistinctSuffixesWithExtraCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the two-suffix extra runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the two-suffix extra runtime test");

        String ownerName = "example/TwoSuffixExtraRuntime";
        String baseName = "example/TwoSuffixExtraRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-two-suffix-extra-run");
        Path inputJar = directory.resolve("two-suffix-extra.jar");
        Path outputDirectory = directory.resolve("output");
        createTwoDistinctSuffixExtraJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain two-suffix extra Java run");
        assertEquals(
                "7" + System.lineSeparator()
                        + "-4" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(2, hiddenBridgeCallCount(transformedConstructor));
        java.util.List<MethodInsnNode> bridges = Arrays.stream(
                        transformedConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .collect(Collectors.toList());
        assertEquals(2, bridges.size());
        assertEquals(1L, bridges.stream()
                .map(invoke -> invoke.owner + "." + invoke.name + invoke.desc)
                .distinct().count());
        assertEquals("(Ljava/lang/Object;III)V", bridges.get(0).desc);

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList(
                                "cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("two-suffix extra CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("two-suffix extra CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Two-suffix extra native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native two-suffix extra Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeDistinctSuffixesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the three-suffix runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the three-suffix runtime test");

        String ownerName = "example/ThreeSuffixRuntime";
        String baseName = "example/ThreeSuffixRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-suffix-run");
        Path inputJar = directory.resolve("three-suffix.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeDistinctSuffixJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain three-suffix multi-super Java run");
        assertEquals(
                "3" + System.lineSeparator()
                        + "4" + System.lineSeparator()
                        + "5" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(3, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1L,
                Arrays.stream(transformedConstructor.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance)
                        .map(MethodInsnNode.class::cast)
                        .filter(invoke ->
                                invoke.getOpcode() == Opcodes.INVOKESTATIC
                                        && invoke.owner.contains("/hidden/"))
                        .map(invoke -> invoke.owner + "." + invoke.name
                                + invoke.desc)
                        .distinct().count());

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList(
                                "cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("three-suffix multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("three-suffix multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Three-suffix native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native three-suffix multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void identicalMultiSuperSuffixCopiesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the identical-suffix runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the identical-suffix runtime test");

        String ownerName = "example/MultiSuperCopiesRuntime";
        String baseName = "example/MultiSuperCopiesRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-multi-super-copies-run");
        Path inputJar = directory.resolve("multi-super-copies.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperIdenticalSuffixCopiesJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain identical-suffix multi-super Java run");
        assertEquals(
                "7" + System.lineSeparator()
                        + "5" + System.lineSeparator()
                        + "-5" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertFalse(realOpcodes(transformedConstructor)
                .contains(Opcodes.PUTFIELD));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("identical-suffix multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("identical-suffix multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Identical-suffix native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native identical-suffix multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void immediateMultiSuperReturnsCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the immediate-return runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the immediate-return runtime test");

        String ownerName = "example/MultiReturnRuntime";
        String baseName = "example/MultiReturnRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-multi-return-run");
        Path inputJar = directory.resolve("multi-return.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperSeparateReturnsJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain immediate-return multi-super Java run");
        assertEquals(
                "7" + System.lineSeparator()
                        + "5" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(2, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("immediate-return multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("immediate-return multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Immediate-return native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native immediate-return multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeImmediateIaddSuperReturnsCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the three-return runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the three-return runtime test");

        String ownerName = "example/ThreeMultiReturnRuntime";
        String baseName = "example/ThreeMultiReturnRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-multi-return-run");
        Path inputJar = directory.resolve("three-multi-return.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeSeparateReturnsJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain three-return multi-super Java run");
        assertEquals(
                "12" + System.lineSeparator()
                        + "22" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IADD));
        assertEquals(2, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("three-return multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("three-return multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Three-return native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native three-return multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeImmediateIsubImulSuperReturnsCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the arithmetic three-return runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the arithmetic three-return runtime test");

        String ownerName = "example/ThreeArithmeticReturnRuntime";
        String baseName = "example/ThreeArithmeticReturnRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-arithmetic-return-run");
        Path inputJar = directory.resolve("three-arithmetic-return.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeIsubImulReturnsJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain arithmetic three-return multi-super Java run");
        assertEquals(
                "9" + System.lineSeparator()
                        + "-44" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.ISUB));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IMUL));
        assertEquals(2, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("arithmetic three-return multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("arithmetic three-return multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Arithmetic three-return native library "
                                    + "was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check(
                "native arithmetic three-return multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeImmediateBitwiseSuperReturnsCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the bitwise three-return runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the bitwise three-return runtime test");

        String ownerName = "example/ThreeBitwiseReturnRuntime";
        String baseName = "example/ThreeBitwiseReturnRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-bitwise-return-run");
        Path inputJar = directory.resolve("three-bitwise-return.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeBitwiseReturnsJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain bitwise three-return multi-super Java run");
        assertEquals(
                "3" + System.lineSeparator()
                        + "-6" + System.lineSeparator()
                        + "-1" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IAND));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IOR));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IXOR));
        assertEquals(2, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("bitwise three-return multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("bitwise three-return multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Bitwise three-return native library "
                                    + "was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check(
                "native bitwise three-return multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeImmediateShiftSuperReturnsCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the shift three-return runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the shift three-return runtime test");

        String ownerName = "example/ThreeShiftReturnRuntime";
        String baseName = "example/ThreeShiftReturnRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-shift-return-run");
        Path inputJar = directory.resolve("three-shift-return.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeShiftReturnsJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain shift three-return multi-super Java run");
        assertEquals(
                "44" + System.lineSeparator()
                        + "1073741818" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.ISHL));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.ISHR));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.IUSHR));
        assertEquals(2, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("shift three-return multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("shift three-return multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Shift three-return native library "
                                    + "was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check(
                "native shift three-return multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void threeIdenticalNonemptySuffixCopiesCompileAndRunWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the three-suffix runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the three-suffix runtime test");

        String ownerName = "example/ThreeMultiSuffixRuntime";
        String baseName = "example/ThreeMultiSuffixRuntimeBase";
        Path directory =
                Files.createTempDirectory("ir-three-multi-suffix-run");
        Path inputJar = directory.resolve("three-multi-suffix.jar");
        Path outputDirectory = directory.resolve("output");
        createMultipleSuperThreeIdenticalSuffixCopiesJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain three-suffix multi-super Java run");
        assertEquals(
                "12" + System.lineSeparator()
                        + "22" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class")))
                    .accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(3, directChainCallCount(
                transformedConstructor, transformed));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertEquals(2, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.GOTO));
        assertEquals(1, Collections.frequency(
                realOpcodes(transformedConstructor), Opcodes.RETURN));
        assertFalse(realOpcodes(transformedConstructor).contains(Opcodes.POP));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("three-suffix multi-super CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("three-suffix multi-super CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Three-suffix native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native three-suffix multi-super Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void identityAstoreZeroCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the ASTORE 0 runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the ASTORE 0 runtime test");

        String ownerName = "example/IdentityAstoreRuntime";
        Path directory = Files.createTempDirectory("ir-astore-zero-run");
        Path inputJar = directory.resolve("astore-zero.jar");
        Path outputDirectory = directory.resolve("output");
        createIdentityAstoreZeroJar(inputJar, ownerName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain ASTORE 0 Java run");
        assertEquals("IDENTITY-ASTORE-0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(variableIndexes(
                transformedConstructor, Opcodes.ASTORE).contains(0));
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("ASTORE 0 CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("ASTORE 0 CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "ASTORE 0 native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native ASTORE 0 Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void prefixOnlyTryCatchConstructorCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the prefix-catch runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the prefix-catch runtime test");

        String ownerName = "example/PrefixCatchRuntime";
        String baseName = "example/PrefixCatchRuntimeBase";
        Path directory = Files.createTempDirectory("ir-prefix-catch-run");
        Path inputJar = directory.resolve("prefix-catch.jar");
        Path outputDirectory = directory.resolve("output");
        createPrefixOnlyTryCatchJar(
                inputJar, ownerName, baseName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain prefix-catch Java run");
        assertEquals(
                "37" + System.lineSeparator()
                        + "37" + System.lineSeparator()
                        + "0" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, transformedConstructor.tryCatchBlocks.size());
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("prefix-catch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("prefix-catch CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Prefix-catch native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native prefix-catch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void relocatedPrefixReturnHandlerCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the relocated-catch runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the relocated-catch runtime test");

        String ownerName = "example/RelocatedCatchRuntime";
        Path directory = Files.createTempDirectory("ir-relocated-catch-run");
        Path inputJar = directory.resolve("relocated-catch.jar");
        Path outputDirectory = directory.resolve("output");
        createRelocatedPrefixReturnHandlerJar(inputJar, ownerName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain relocated-catch Java run");
        assertEquals(
                "4" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(transformedConstructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertFalse(realOpcodes(transformedConstructor).contains(Opcodes.POP));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("relocated-catch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("relocated-catch CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Relocated-catch native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native relocated-catch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void relocatedPrefixGotoReturnHandlerCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the relocated-goto-catch runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the relocated-goto-catch runtime test");

        String ownerName = "example/RelocatedGotoCatchRuntime";
        Path directory =
                Files.createTempDirectory("ir-relocated-goto-catch-run");
        Path inputJar = directory.resolve("relocated-goto-catch.jar");
        Path outputDirectory = directory.resolve("output");
        createRelocatedPrefixGotoReturnHandlerJar(inputJar, ownerName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain relocated-goto-catch Java run");
        assertEquals(
                "4" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(transformedConstructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertFalse(realOpcodes(transformedConstructor).contains(Opcodes.POP));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("relocated-goto-catch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("relocated-goto-catch CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Relocated-goto-catch native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native relocated-goto-catch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void relocatedPrefixAstoreReturnHandlerCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the relocated ASTORE runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the relocated ASTORE runtime test");

        String ownerName = "example/RelocatedAstoreCatchRuntime";
        Path directory =
                Files.createTempDirectory("ir-relocated-astore-catch-run");
        Path inputJar = directory.resolve("relocated-astore-catch.jar");
        Path outputDirectory = directory.resolve("output");
        createRelocatedPrefixAstoreReturnHandlerJar(inputJar, ownerName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain relocated ASTORE catch Java run");
        assertEquals(
                "4" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(transformedConstructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertFalse(realOpcodes(transformedConstructor)
                .contains(Opcodes.ASTORE));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("relocated ASTORE catch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("relocated ASTORE catch CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Relocated ASTORE catch native library "
                                    + "was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native relocated ASTORE catch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void relocatedPrefixAthrowHandlerCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the relocated ATHROW runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the relocated ATHROW runtime test");

        String ownerName = "example/RelocatedAthrowCatchRuntime";
        Path directory =
                Files.createTempDirectory("ir-relocated-athrow-catch-run");
        Path inputJar = directory.resolve("relocated-athrow-catch.jar");
        Path outputDirectory = directory.resolve("output");
        createRelocatedPrefixAthrowHandlerJar(inputJar, ownerName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-jar", inputJar.toString()));
        javaResult.check("plain relocated ATHROW catch Java run");
        assertEquals(
                "4" + System.lineSeparator()
                        + "0" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(transformedConstructor.tryCatchBlocks.isEmpty());
        assertEquals(1, hiddenBridgeCallCount(transformedConstructor));
        assertFalse(realOpcodes(transformedConstructor)
                .contains(Opcodes.ATHROW));

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("relocated ATHROW catch CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".",
                                "--config", "Release"))
                .check("relocated ATHROW catch CMake build");

        Path library;
        try (Stream<Path> files =
                     Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Relocated ATHROW catch native library "
                                    + "was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native relocated ATHROW catch Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    private void prefixExtraLocalCompilesAndRunsWithJavaParity(
            String directoryPrefix, String ownerName, boolean gapped)
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the prefix-extra runtime test");

        Path directory = Files.createTempDirectory(directoryPrefix);
        Path inputJar = directory.resolve("prefix-extra.jar");
        Path outputDirectory = directory.resolve("output");
        createPrefixExtraLocalJar(inputJar, ownerName, gapped);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain prefix-extra Java run");
        assertEquals("PREFIX-EXTRA-FORWARDED" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        MethodInsnNode bridge = Arrays.stream(
                        transformedConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(
                "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)V",
                bridge.desc);

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("prefix-extra CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("prefix-extra CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Prefix-extra native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native prefix-extra Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void rejectsPrefixWritesToConstructorReceiverBeforeMutation()
            throws Exception {
        Class<?> receiverClass = rejectConstructorReceiverWrite(
                "example/ReceiverPrefix",
                receiverReassignedBeforeSuperConstructor());
        receiverClass.getConstructor(Object.class).newInstance(new Object());
    }

    @Test
    public void prefixReferenceParameterAstoreCompilesAndRunsWithJavaParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the flex-constructor runtime test");

        Path directory = Files.createTempDirectory("ir-flex-ctor-run");
        Path inputJar = directory.resolve("flex-ctor.jar");
        Path outputDirectory = directory.resolve("output");
        createReferenceParameterAstoreJar(inputJar);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain flex-constructor Java run");
        assertEquals("forwarded-result" + System.lineSeparator(), javaResult.stdout);

        String ownerName = "example/FlexCtorAstore";
        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        MethodNode transformedConstructor = transformed.methods.stream()
                .filter(method -> "<init>".equals(method.name))
                .findFirst().orElseThrow(AssertionError::new);
        MethodInsnNode bridge = Arrays.stream(transformedConstructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESTATIC
                        && invoke.owner.contains("/hidden/"))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V",
                bridge.desc);

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("flex-constructor CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("flex-constructor CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Flex-constructor native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native flex-constructor Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void rewrittenConstructorPassesJvmVerification() throws Exception {
        ClassNode owner = constructorOwner("example/Verified", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        MethodNode constructor = intFieldConstructor();
        owner.methods.add(constructor);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        for (ClassNode hidden : obfuscator.getHiddenMethodsPool().getClasses()) {
            loader.define(writeClass(hidden));
        }
        Class<?> verified = loader.define(writeClass(owner));
        InvocationTargetException error = assertThrows(
                InvocationTargetException.class,
                () -> verified.getConstructor(int.class).newInstance(7));
        assertTrue(error.getCause() instanceof UnsatisfiedLinkError);
        assertEquals(0, constructor.access & Opcodes.ACC_NATIVE);
    }

    @Test
    public void lowersExpandedInvokeFamilies() {
        NativeObfuscator obfuscator = new NativeObfuscator();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));

        MethodContext longContext = new MethodContext(obfuscator,
                staticLongInvokeMethod(), 0, owner(), 0);
        compiler.processMethod(longContext);
        assertTrue(longContext.output.toString().contains("env->CallStaticLongMethod"));

        MethodContext virtualStringContext = new MethodContext(obfuscator,
                virtualStringInvokeMethod(), 1, owner(), 0);
        compiler.processMethod(virtualStringContext);
        assertTrue(virtualStringContext.output.toString().contains("env->CallObjectMethod"));
        assertTrue(virtualStringContext.output.toString().contains("env->GetStringLength"));

        MethodContext staticStringContext = new MethodContext(obfuscator,
                staticStringInvokeMethod(), 2, owner(), 0);
        compiler.processMethod(staticStringContext);
        assertTrue(staticStringContext.output.toString()
                .contains("env->CallStaticObjectMethod"));

        MethodContext staticVoidContext = new MethodContext(obfuscator,
                staticVoidLongInvokeMethod(), 3, owner(), 0);
        compiler.processMethod(staticVoidContext);
        assertTrue(staticVoidContext.output.toString().contains("env->CallStaticVoidMethod"));
    }

    @Test
    public void lowersInterfaceInvokeFamiliesWithExactDescriptorArguments() {
        MethodNode intMethod = interfaceInvokeMethod(
                "interfaceInt", "adjust", "(I)I");
        IrNodes.Invoke intInvoke = onlyInvoke(frontend.build("example/Math", intMethod));
        assertEquals(IrNodes.Invoke.Kind.INTERFACE, intInvoke.getKind());
        assertEquals(1, intInvoke.getArguments().size());
        assertEquals(IrType.I32, intInvoke.getArguments().get(0).getType());
        assertEquals(IrType.I32, intInvoke.getResult().getType());
        String intCpp = compileToCpp(intMethod, 0);
        assertTrue(intCpp.contains("env->GetMethodID(cclasses[0]"));
        assertTrue(intCpp.contains(
                "env->CallIntMethod(arg0, cmethods[0], arg1)"));

        MethodNode longMethod = interfaceInvokeMethod(
                "interfaceLong", "adjustLong", "(J)J");
        IrNodes.Invoke longInvoke = onlyInvoke(frontend.build("example/Math", longMethod));
        assertEquals(1, longInvoke.getArguments().size());
        assertEquals(IrType.I64, longInvoke.getArguments().get(0).getType());
        assertEquals(IrType.I64, longInvoke.getResult().getType());
        assertTrue(compileToCpp(longMethod, 0).contains(
                "env->CallLongMethod(arg0, cmethods[0], arg1)"));

        MethodNode objectMethod = interfaceInvokeMethod(
                "interfaceObject", "create", "(I)Ljava/lang/Object;");
        IrNodes.Invoke objectInvoke =
                onlyInvoke(frontend.build("example/Math", objectMethod));
        assertEquals(1, objectInvoke.getArguments().size());
        assertEquals(IrType.REFERENCE, objectInvoke.getResult().getType());
        assertTrue(compileToCpp(objectMethod, 0).contains(
                "env->CallObjectMethod(arg0, cmethods[0], arg1)"));

        MethodNode voidMethod = interfaceInvokeMethod(
                "interfaceVoid", "accept", "(JLjava/lang/Object;)V");
        IrNodes.Invoke voidInvoke = onlyInvoke(frontend.build("example/Math", voidMethod));
        assertEquals(2, voidInvoke.getArguments().size());
        assertEquals(IrType.I64, voidInvoke.getArguments().get(0).getType());
        assertEquals(IrType.REFERENCE, voidInvoke.getArguments().get(1).getType());
        assertEquals(null, voidInvoke.getResult());
        assertTrue(compileToCpp(voidMethod, 0).contains(
                "env->CallVoidMethod(arg0, cmethods[0], arg1, arg2)"));
    }

    @Test
    public void lowersNonConstructorSpecialInvokeFamilies() {
        MethodNode intMethod = specialInvokeMethod(
                "specialInt", "example/Base", "superInt", "(I)I");
        IrNodes.Invoke intInvoke = onlyInvoke(frontend.build("example/Math", intMethod));
        assertEquals(IrNodes.Invoke.Kind.SPECIAL, intInvoke.getKind());
        assertEquals("superInt", intInvoke.getName());
        assertEquals(1, intInvoke.getArguments().size());
        assertEquals(IrType.I32, intInvoke.getResult().getType());
        String intCpp = compileToCpp(intMethod, 0);
        assertTrue(intCpp.contains("env->GetMethodID(cclasses[0]"));
        assertTrue(intCpp.contains(
                "env->CallNonvirtualIntMethod(obj, cclasses[0], cmethods[0], arg0)"));

        MethodNode longMethod = specialInvokeMethod(
                "specialLong", "example/Math", "privateLong", "(J)J");
        IrNodes.Invoke longInvoke = onlyInvoke(frontend.build("example/Math", longMethod));
        assertEquals(IrType.I64, longInvoke.getResult().getType());
        assertTrue(compileToCpp(longMethod, 0).contains(
                "env->CallNonvirtualLongMethod(obj, cclasses[0], cmethods[0], arg0)"));

        MethodNode objectMethod = specialInvokeMethod(
                "specialObject", "example/Base", "superText",
                "()Ljava/lang/String;");
        IrNodes.Invoke objectInvoke =
                onlyInvoke(frontend.build("example/Math", objectMethod));
        assertEquals(0, objectInvoke.getArguments().size());
        assertEquals(IrType.REFERENCE, objectInvoke.getResult().getType());
        assertTrue(compileToCpp(objectMethod, 0).contains(
                "env->CallNonvirtualObjectMethod(obj, cclasses[0], cmethods[0])"));

        MethodNode voidMethod = specialInvokeMethod(
                "specialVoid", "example/Base", "superAccept",
                "(JLjava/lang/Object;)V");
        IrNodes.Invoke voidInvoke = onlyInvoke(frontend.build("example/Math", voidMethod));
        assertEquals(2, voidInvoke.getArguments().size());
        assertEquals(null, voidInvoke.getResult());
        assertTrue(compileToCpp(voidMethod, 0).contains(
                "env->CallNonvirtualVoidMethod(obj, cclasses[0], cmethods[0], "
                        + "arg0, arg1)"));
    }

    @Test
    public void lowersSmallPrimitiveInvokeArgumentsAndReturnsWithExactJniFamilies() {
        String[] descriptors = {"Z", "B", "C", "S"};
        String[] carriers = {"Boolean", "Byte", "Char", "Short"};
        String[] jniTypes = {"jboolean", "jbyte", "jchar", "jshort"};
        for (int i = 0; i < descriptors.length; i++) {
            String descriptor = "(" + descriptors[i] + ")" + descriptors[i];
            assertSmallPrimitiveInvoke(staticPrimitiveInvokeMethod(
                            "static" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.STATIC, "CallStatic" + carriers[i] + "Method",
                    jniTypes[i], "arg0");
            assertSmallPrimitiveInvoke(virtualPrimitiveInvokeMethod(
                            "virtual" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.VIRTUAL, "Call" + carriers[i] + "Method",
                    jniTypes[i], "arg1");
            assertSmallPrimitiveInvoke(interfaceInvokeMethod(
                            "interface" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.INTERFACE, "Call" + carriers[i] + "Method",
                    jniTypes[i], "arg1");
            assertSmallPrimitiveInvoke(specialInvokeMethod(
                            "special" + carriers[i], "example/Base",
                            "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.SPECIAL,
                    "CallNonvirtual" + carriers[i] + "Method", jniTypes[i], "arg0");
        }
    }

    @Test
    public void nullInterfaceReceiverUsesSharedExceptionalExit() {
        MethodNode method = nullableInterfaceInvokeMethod();
        IrMethod ir = frontend.build("example/Math", method);
        IrNodes.Invoke invoke = onlyInvoke(ir);
        IrBlock invokeBlock = ir.getBlocks().stream()
                .filter(block -> block.getInstructions().contains(invoke))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("java/lang/NullPointerException",
                invokeBlock.getExceptionEdges().get(0).getCatchType());

        String cpp = compileToCpp(method, 0);
        int nullCheck = cpp.indexOf("if (arg0 == nullptr)");
        int throwCall = cpp.indexOf("utils::throw_re", nullCheck);
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", throwCall);
        int invokeCall = cpp.indexOf("env->CallIntMethod", dispatch);
        assertTrue(nullCheck >= 0 && throwCall > nullCheck
                && dispatch > throwCall && invokeCall > dispatch);
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->IsInstanceOf((jobject) caught_exception"));
    }

    @Test
    public void lowersFloatAndDoubleInvokeArgumentsAndReturnsWithExactJniFamilies() {
        String[] descriptors = {"F", "D"};
        IrType[] types = {IrType.F32, IrType.F64};
        String[] carriers = {"Float", "Double"};
        for (int i = 0; i < descriptors.length; i++) {
            String descriptor = "(" + descriptors[i] + ")" + descriptors[i];
            assertFloatingInvoke(staticPrimitiveInvokeMethod(
                            "static" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.STATIC, types[i],
                    "CallStatic" + carriers[i] + "Method");
            assertFloatingInvoke(virtualPrimitiveInvokeMethod(
                            "virtual" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.VIRTUAL, types[i],
                    "Call" + carriers[i] + "Method");
            assertFloatingInvoke(interfaceInvokeMethod(
                            "interface" + carriers[i], "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.INTERFACE, types[i],
                    "Call" + carriers[i] + "Method");
            assertFloatingInvoke(specialInvokeMethod(
                            "special" + carriers[i], "example/Base",
                            "identity" + carriers[i], descriptor),
                    IrNodes.Invoke.Kind.SPECIAL, types[i],
                    "CallNonvirtual" + carriers[i] + "Method");
        }
    }

    @Test
    public void admitsInvokedynamicThroughIndyPreprocessorInIr() {
        MethodNode method = invokedynamicMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertFalse(cpp.isEmpty());
        // The frontend ran the shared indy preprocessor: the call site became the
        // lookup/classloader intrinsics plus a bootstrap invoke, all recognised by
        // the IR backend rather than emitted as symbolic native.magic calls.
        assertTrue(cpp.contains("utils::get_lookup(env, clazz)"));
        assertTrue(cpp.contains("= classloader;"));
        assertFalse(cpp.contains("native.magic"));
        assertFalse(cpp.contains("native/magic"));
        assertTrue(obfuscator.getCachedMethods().size() > 0);
    }

    @Test
    public void rejectsUnlowerableConstantDynamicLdcBeforeMutation() {
        MethodNode method = unlowerableConstantDynamicMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.LDC, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void admitsStringAndIntConstantDynamicOnHiddenInterfaceCompanion()
            throws Exception {
        String counterName = "example/InterfaceCondyCounterUnit";
        ClassNode counter = interfaceConstantDynamicCounter(counterName);
        ClassNode owner = interfaceConstantDynamicOwner(
                "example/InterfaceCondyUnit", counterName);
        MethodNode method = interfaceConstantDynamicCombinedMethod(owner.name);
        owner.methods.add(method);
        NativeObfuscator obfuscator = new NativeObfuscator();
        String companionName = obfuscator.getHiddenMethodsPool()
                .getCompanionClassName(owner.name);

        IrMethod lowered = frontend.build(owner.name, companionName, method);
        List<IrNodes.Invoke> resolverCalls = lowered.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.Invoke.class::isInstance)
                .map(IrNodes.Invoke.class::cast)
                .filter(invoke -> invoke.getKind() == IrNodes.Invoke.Kind.STATIC
                        && companionName.equals(invoke.getOwner())
                        && invoke.getName().startsWith("$native$condy$"))
                .collect(Collectors.toList());
        assertEquals(2, resolverCalls.size());

        MethodContext context = new MethodContext(
                obfuscator, method, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(context.output.toString().contains("env->CallStaticObjectMethod"));
        assertTrue(context.output.toString().contains("env->CallStaticIntMethod"));
        assertTrue(owner.fields.isEmpty());
        assertEquals(1, obfuscator.getHiddenMethodsPool().getClasses().size());
        ClassNode companion =
                obfuscator.getHiddenMethodsPool().getClasses().get(0);
        assertEquals(companionName, companion.name);
        assertTrue(companion.name.startsWith("native0/hidden/HiddenCondy$"));
        assertEquals(6L, companion.fields.stream()
                .filter(field -> field.name.startsWith("$native$condy$"))
                .count());
        assertEquals(0L, owner.methods.stream()
                .filter(candidate -> candidate.name.startsWith("$native$condy$")
                        && (candidate.access & Opcodes.ACC_SYNCHRONIZED) != 0)
                .count());

        MethodNode stringResolver = companion.methods.stream()
                .filter(candidate -> candidate.name.startsWith("$native$condy$")
                        && "()Ljava/lang/String;".equals(candidate.desc)
                        && (candidate.access & Opcodes.ACC_SYNCHRONIZED) != 0)
                .findFirst().orElseThrow(AssertionError::new);
        MethodNode intResolver = companion.methods.stream()
                .filter(candidate -> candidate.name.startsWith("$native$condy$")
                        && "()I".equals(candidate.desc)
                        && (candidate.access & Opcodes.ACC_SYNCHRONIZED) != 0)
                .findFirst().orElseThrow(AssertionError::new);

        ByteArrayClassLoader loader = new ByteArrayClassLoader();
        Class<?> generatedCounter = loader.define(writeClass(counter));
        Class<?> generatedCompanion = loader.define(writeClass(companion));
        loader.define(writeClass(owner));
        Method stringResolverMethod =
                generatedCompanion.getDeclaredMethod(stringResolver.name);
        Method intResolverMethod =
                generatedCompanion.getDeclaredMethod(intResolver.name);
        assertEquals("example.InterfaceCondyUnit",
                stringResolverMethod.invoke(null));
        assertEquals("example.InterfaceCondyUnit",
                stringResolverMethod.invoke(null));
        assertEquals(21, intResolverMethod.invoke(null));
        assertEquals(21, intResolverMethod.invoke(null));
        assertEquals(1, generatedCounter.getField("stringCalls").getInt(null));
        assertEquals(1, generatedCounter.getField("intCalls").getInt(null));
    }

    @Test
    public void rejectsUnsafeInterfaceConstantDynamicBeforeMutation() {
        ClassNode owner = interfaceConstantDynamicOwner(
                "example/UnsafeInterfaceCondy",
                "example/UnsafeInterfaceCondyCounter");
        MethodNode method = unlowerableConstantDynamicMethod();
        owner.methods.add(method);
        List<AbstractInsnNode> originalInstructions =
                Arrays.asList(method.instructions.toArray());
        int originalMethodCount = owner.methods.size();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(
                obfuscator, method, 0, owner, 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.LDC, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "bootstrap is not REF_invokeStatic"));
        assertEquals(originalInstructions,
                Arrays.asList(method.instructions.toArray()));
        assertEquals(originalMethodCount, owner.methods.size());
        assertTrue(owner.fields.isEmpty());
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void admitsAndCachesStringConstantDynamicThroughSyntheticResolver()
            throws Exception {
        ClassNode owner = constantDynamicOwner();
        MethodNode method = constantDynamicStringMethod(owner.name);
        owner.methods.add(method);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(context.output.toString().contains("env->CallStaticObjectMethod"));
        MethodNode resolver = owner.methods.stream()
                .filter(candidate -> candidate.name.startsWith("$native$condy$"))
                .reduce((first, second) -> second)
                .orElseThrow(AssertionError::new);
        assertTrue((resolver.access & Opcodes.ACC_SYNCHRONIZED) != 0);

        Class<?> generated = new ByteArrayClassLoader().define(writeClass(owner));
        Method resolverMethod = generated.getDeclaredMethod(resolver.name);
        resolverMethod.setAccessible(true);
        assertEquals("condy-value", resolverMethod.invoke(null));
        assertEquals("condy-value", resolverMethod.invoke(null));
        assertEquals(1, generated.getField("bootstrapCalls").getInt(null));
        assertEquals(1, generated.getField("outerBootstrapCalls").getInt(null));
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
    }

    @Test
    public void admitsPrimitiveConstantDynamicThroughTypedResolver()
            throws Exception {
        ClassNode owner = constantDynamicOwner();
        MethodNode method = constantDynamicIntMethod(owner.name);
        owner.methods.add(method);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        assertTrue(context.output.toString().contains("env->CallStaticIntMethod"));
        MethodNode resolver = owner.methods.stream()
                .filter(candidate -> candidate.name.startsWith("$native$condy$")
                        && "()I".equals(candidate.desc))
                .findFirst().orElseThrow(AssertionError::new);
        Class<?> generated = new ByteArrayClassLoader().define(writeClass(owner));
        Method resolverMethod = generated.getDeclaredMethod(resolver.name);
        resolverMethod.setAccessible(true);
        assertEquals(42, resolverMethod.invoke(null));
        assertEquals(42, resolverMethod.invoke(null));
    }

    @Test
    public void executesStringConstantDynamicThroughIrWhenToolchainAvailable()
            throws Exception {
        ClassNode owner = constantDynamicOwner();
        MethodNode method = constantDynamicStringMethod(owner.name);
        owner.methods.add(method);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);
        String body = context.output.toString();
        String function = generatedFunction(body);
        int classes = Math.max(1, obfuscator.getCachedClasses().size());
        int methods = Math.max(1, obfuscator.getCachedMethods().size());
        int strings = Math.max(1, obfuscator.getCachedStrings().size());
        int fields = Math.max(1, obfuscator.getCachedFields().size());

        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "#include <cstdarg>\n"
                + "#include <mutex>\n"
                + "#include <unordered_set>\n"
                + "static const jobject CLASSLOADER = reinterpret_cast<jobject>(0x1001);\n"
                + "static const jclass RESOLVED = reinterpret_cast<jclass>(0x1002);\n"
                + "static const jobject CONDY = reinterpret_cast<jobject>(0x4242);\n"
                + "static const jmethodID MID = reinterpret_cast<jmethodID>(0x2001);\n"
                + "static int g_resolver_calls = 0;\n"
                + "static jboolean f_exception_check(JNIEnv *) { return JNI_FALSE; }\n"
                + "static jboolean f_is_same(JNIEnv *, jobject, jobject) { return JNI_FALSE; }\n"
                + "static jobject f_new_weak(JNIEnv *, jobject value) { return value; }\n"
                + "static void f_delete_local(JNIEnv *, jobject) {}\n"
                + "static jmethodID f_get_static_mid(JNIEnv *, jclass, const char *, const char *)"
                + " { return MID; }\n"
                + "static jobject f_call_static_object(JNIEnv *, jclass, jmethodID, va_list)"
                + " { ++g_resolver_calls; return CONDY; }\n"
                + "static jsize f_get_string_length(JNIEnv *, jstring value)"
                + " { return value == CONDY ? 11 : -1; }\n"
                + "namespace native_jvm {\n"
                + "namespace utils {\n"
                + "jobject get_classloader_from_class(JNIEnv *, jclass) { return CLASSLOADER; }\n"
                + "jclass find_class_wo_static(JNIEnv *, jobject, jstring) { return RESOLVED; }\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int) {}\n"
                + "}\n"
                + "namespace classes { namespace condyns {\n"
                + "char string_pool_storage[4096] = {};\n"
                + "char *string_pool = string_pool_storage;\n"
                + "jstring cstrings[" + strings + "] = {};\n"
                + "std::mutex cclasses_mtx[" + classes + "];\n"
                + "jclass cclasses[" + classes + "] = {};\n"
                + "jmethodID cmethods[" + methods + "] = {};\n"
                + "jfieldID cfields[" + fields + "] = {};\n"
                + body
                + "} }\n"
                + "}\n"
                + "int main() {\n"
                + "    JNINativeInterface_ functions = {};\n"
                + "    functions.ExceptionCheck = f_exception_check;\n"
                + "    functions.IsSameObject = f_is_same;\n"
                + "    functions.NewWeakGlobalRef = f_new_weak;\n"
                + "    functions.DeleteLocalRef = f_delete_local;\n"
                + "    functions.GetStaticMethodID = f_get_static_mid;\n"
                + "    functions.CallStaticObjectMethodV = f_call_static_object;\n"
                + "    functions.GetStringLength = f_get_string_length;\n"
                + "    JNIEnv_ env_value = {};\n"
                + "    env_value.functions = &functions;\n"
                + "    JNIEnv *env = &env_value;\n"
                + "    jclass clazz = reinterpret_cast<jclass>(0x9001);\n"
                + "    jint first = native_jvm::classes::condyns::" + function
                + "(env, clazz);\n"
                + "    jint second = native_jvm::classes::condyns::" + function
                + "(env, clazz);\n"
                + "    if (first != 11 || second != 11) return 1;\n"
                + "    if (g_resolver_calls != 2) return 2;\n"
                + "    return 0;\n"
                + "}\n";
        compileAndRunCppHarness("ir-condy-run", source,
                "Lowered ConstantDynamic did not return the cached resolver value");
    }

    @Test
    public void interfaceConstantDynamicCompilesAndRunsWithHotSpotParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the interface ConstantDynamic runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the interface ConstantDynamic runtime test");

        String interfaceName = "example/InterfaceCondyRuntime";
        String counterName = "example/InterfaceCondyRuntimeCounter";
        String mainName = "example/InterfaceCondyRuntimeMain";
        Path directory = Files.createTempDirectory("ir-interface-condy-run");
        Path inputJar = directory.resolve("interface-condy.jar");
        Path outputDirectory = directory.resolve("output");
        createInterfaceConstantDynamicJar(
                inputJar, interfaceName, counterName, mainName);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", inputJar.toString()));
        javaResult.check("plain interface ConstantDynamic Java run");
        assertEquals(
                "example.InterfaceCondyRuntimeexample.InterfaceCondyRuntime"
                        + System.lineSeparator()
                        + "42" + System.lineSeparator()
                        + "1" + System.lineSeparator()
                        + "1" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Arrays.asList(counterName, mainName,
                        interfaceName + "#stringBootstrap!"
                                + "(Ljava/lang/invoke/MethodHandles$Lookup;"
                                + "Ljava/lang/String;Ljava/lang/Class;)"
                                + "Ljava/lang/String;",
                        interfaceName + "#intBootstrap!"
                                + "(Ljava/lang/invoke/MethodHandles$Lookup;"
                                + "Ljava/lang/String;Ljava/lang/Class;)I"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformedInterface = new ClassNode(Opcodes.ASM9);
        ClassNode companion = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(interfaceName + ".class")))
                    .accept(transformedInterface, 0);
            JarEntry companionEntry = jar.stream()
                    .filter(entry -> entry.getName()
                            .startsWith("native0/hidden/HiddenCondy$"))
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "Interface ConstantDynamic companion is missing"));
            new org.objectweb.asm.ClassReader(jar.getInputStream(companionEntry))
                    .accept(companion, 0);
        }
        assertEquals(0L, transformedInterface.fields.stream()
                .filter(field -> field.name.startsWith("$native$condy$"))
                .count());
        assertEquals(2L, companion.methods.stream()
                .filter(method -> method.name.startsWith("$native$condy$")
                        && (method.access & Opcodes.ACC_SYNCHRONIZED) != 0)
                .count());
        assertEquals(6L, companion.fields.stream()
                .filter(field -> field.name.startsWith("$native$condy$"))
                .count());

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("interface ConstantDynamic CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("interface ConstantDynamic CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Interface ConstantDynamic native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native interface ConstantDynamic Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void executesRawMethodTypeLdcThroughIrWhenToolchainAvailable()
            throws Exception {
        MethodNode method = rawMethodTypeUseMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(
                obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);
        String body = context.output.toString();
        String function = generatedFunction(body);
        int classes = Math.max(1, obfuscator.getCachedClasses().size());
        int methods = Math.max(1, obfuscator.getCachedMethods().size());
        int strings = Math.max(1, obfuscator.getCachedStrings().size());
        int fields = Math.max(1, obfuscator.getCachedFields().size());

        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "#include <cstdarg>\n"
                + "#include <mutex>\n"
                + "#include <unordered_set>\n"
                + "static const jobject CLASSLOADER = reinterpret_cast<jobject>(0x1001);\n"
                + "static const jclass RESOLVED = reinterpret_cast<jclass>(0x1002);\n"
                + "static const jobject METHODTYPE = reinterpret_cast<jobject>(0x4242);\n"
                + "static const jmethodID MID = reinterpret_cast<jmethodID>(0x2001);\n"
                + "static jboolean f_exception_check(JNIEnv *) { return JNI_FALSE; }\n"
                + "static jboolean f_is_same(JNIEnv *, jobject, jobject) { return JNI_FALSE; }\n"
                + "static jobject f_new_weak(JNIEnv *, jobject value) { return value; }\n"
                + "static void f_delete_local(JNIEnv *, jobject) {}\n"
                + "static jmethodID f_get_static_mid(JNIEnv *, jclass, const char *, const char *)"
                + " { return MID; }\n"
                + "static jmethodID f_get_mid(JNIEnv *, jclass, const char *, const char *)"
                + " { return MID; }\n"
                + "static jobject f_call_static_object(JNIEnv *, jclass, jmethodID, va_list)"
                + " { return METHODTYPE; }\n"
                + "static jint f_call_int(JNIEnv *, jobject receiver, jmethodID, va_list)"
                + " { return receiver == METHODTYPE ? 2 : -1; }\n"
                + "namespace native_jvm {\n"
                + "namespace utils {\n"
                + "jobject get_classloader_from_class(JNIEnv *, jclass) { return CLASSLOADER; }\n"
                + "jclass find_class_wo_static(JNIEnv *, jobject, jstring) { return RESOLVED; }\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int) {}\n"
                + "}\n"
                + "namespace classes { namespace methodtypens {\n"
                + "char string_pool_storage[4096] = {};\n"
                + "char *string_pool = string_pool_storage;\n"
                + "jstring cstrings[" + strings + "] = {};\n"
                + "std::mutex cclasses_mtx[" + classes + "];\n"
                + "jclass cclasses[" + classes + "] = {};\n"
                + "jmethodID cmethods[" + methods + "] = {};\n"
                + "jfieldID cfields[" + fields + "] = {};\n"
                + body
                + "} }\n"
                + "}\n"
                + "int main() {\n"
                + "    JNINativeInterface_ functions = {};\n"
                + "    functions.ExceptionCheck = f_exception_check;\n"
                + "    functions.IsSameObject = f_is_same;\n"
                + "    functions.NewWeakGlobalRef = f_new_weak;\n"
                + "    functions.DeleteLocalRef = f_delete_local;\n"
                + "    functions.GetStaticMethodID = f_get_static_mid;\n"
                + "    functions.GetMethodID = f_get_mid;\n"
                + "    functions.CallStaticObjectMethodV = f_call_static_object;\n"
                + "    functions.CallIntMethodV = f_call_int;\n"
                + "    JNIEnv_ env_value = {};\n"
                + "    env_value.functions = &functions;\n"
                + "    JNIEnv *env = &env_value;\n"
                + "    jclass clazz = reinterpret_cast<jclass>(0x9001);\n"
                + "    jint result = native_jvm::classes::methodtypens::" + function
                + "(env, clazz);\n"
                + "    return result == 2 ? 0 : 1;\n"
                + "}\n";
        compileAndRunCppHarness("ir-methodtype-run", source,
                "Lowered raw MethodType LDC did not execute its use");
    }

    @Test
    public void routesConstantDynamicResolverFailureThroughCatchDispatch() {
        ClassNode owner = constantDynamicOwner();
        MethodNode method = constantDynamicCatchMethod(owner.name);
        owner.methods.add(method);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        String cpp = context.output.toString();
        int resolverCall = cpp.indexOf("env->CallStaticObjectMethod");
        assertTrue(resolverCall >= 0);
        assertTrue(cpp.indexOf("env->ExceptionCheck()", resolverCall) > resolverCall);
        assertTrue(cpp.indexOf("goto IR_CATCH_0;", resolverCall) > resolverCall);
    }

    @Test
    public void admitsStringConcatIndyThroughFullPreprocessorPipeline() {
        // A real StringConcatFactory call site, lowered by the production
        // preprocessing pass and then compiled on the IR path with no fallback.
        MethodNode method = stringConcatIndyMethod();
        ClassNode owner = owner();
        by.radioegor146.bytecode.PreprocessorRunner.preprocess(owner, method,
                Platform.STD_JAVA);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertFalse(cpp.isEmpty());
        assertTrue(cpp.contains("utils::get_lookup(env, clazz)"));
        assertTrue(cpp.contains("env->CallStaticObjectMethod"));
        assertFalse(cpp.contains("native.magic"));
        assertFalse(cpp.contains("native/magic"));
    }

    @Test
    public void executesStringConcatIndyThroughIrWhenToolchainAvailable() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR invokedynamic runtime test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR invokedynamic runtime test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR invokedynamic runtime test");

        MethodNode method = stringConcatIndyMethod();
        ClassNode owner = owner();
        by.radioegor146.bytecode.PreprocessorRunner.preprocess(owner, method,
                Platform.STD_JAVA);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner, 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        String body = context.output.toString();

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(__ngen_[A-Za-z0-9_]+)\\(JNIEnv").matcher(body);
        assertTrue(matcher.find(), "generated indy function not found");
        String function = matcher.group(1);
        int classes = Math.max(1, obfuscator.getCachedClasses().size());
        int methods = Math.max(1, obfuscator.getCachedMethods().size());
        int strings = Math.max(1, obfuscator.getCachedStrings().size());
        int fields = Math.max(1, obfuscator.getCachedFields().size());

        // A fake JNIEnv drives the lowered bytecode end to end: build the argument
        // array, run the bootstrap through the static-invoke sentinels, fetch the
        // call-site target, then MethodHandle.invokeWithArguments. The second
        // CallObjectMethodV (invokeWithArguments) returns RESULT, so a program that
        // walks the whole lowered graph must hand RESULT back.
        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "#include <cstdarg>\n"
                + "#include <mutex>\n"
                + "#include <unordered_set>\n"
                + "static const jobject CLASSLOADER = reinterpret_cast<jobject>(0x1001);\n"
                + "static const jclass RESOLVED = reinterpret_cast<jclass>(0x1002);\n"
                + "static const jobject LOOKUP = reinterpret_cast<jobject>(0x1003);\n"
                + "static const jobjectArray ARRAY = reinterpret_cast<jobjectArray>(0x1004);\n"
                + "static const jmethodID SMID = reinterpret_cast<jmethodID>(0x2001);\n"
                + "static const jmethodID VMID = reinterpret_cast<jmethodID>(0x2002);\n"
                + "static const jobject METHODTYPE = reinterpret_cast<jobject>(0x4001);\n"
                + "static const jobject CALLSITE = reinterpret_cast<jobject>(0x4002);\n"
                + "static const jobject METHODHANDLE = reinterpret_cast<jobject>(0x4003);\n"
                + "static const jobject RESULT = reinterpret_cast<jobject>(0x4242);\n"
                + "static const jobject BAD = reinterpret_cast<jobject>(0xDEAD);\n"
                + "static bool g_threw = false;\n"
                + "static int g_call_static = 0;\n"
                + "static int g_call_object = 0;\n"
                + "static jboolean f_exception_check(JNIEnv *) { return JNI_FALSE; }\n"
                + "static jboolean f_is_same(JNIEnv *, jobject, jobject) { return JNI_FALSE; }\n"
                + "static jobject f_new_weak(JNIEnv *, jobject o) { return o; }\n"
                + "static void f_delete_local(JNIEnv *, jobject) {}\n"
                + "static jobjectArray f_new_object_array(JNIEnv *, jsize, jclass, jobject)"
                + " { return ARRAY; }\n"
                + "static void f_set_object_array(JNIEnv *, jobjectArray, jsize, jobject) {}\n"
                + "static jmethodID f_get_static_mid(JNIEnv *, jclass, const char *, const char *)"
                + " { return SMID; }\n"
                + "static jmethodID f_get_mid(JNIEnv *, jclass, const char *, const char *)"
                + " { return VMID; }\n"
                + "static jobject f_call_static_object(JNIEnv *, jclass, jmethodID, va_list)"
                + " { return ++g_call_static == 1 ? METHODTYPE : CALLSITE; }\n"
                + "static jobject f_call_object(JNIEnv *, jobject, jmethodID, va_list)"
                + " { return ++g_call_object == 1 ? METHODHANDLE : RESULT; }\n"
                + "static jboolean f_is_instance_of(JNIEnv *, jobject, jclass) { return JNI_TRUE; }\n"
                + "static jthrowable f_exception_occurred(JNIEnv *) { return nullptr; }\n"
                + "static void f_exception_clear(JNIEnv *) {}\n"
                + "static jint f_throw(JNIEnv *, jthrowable) { g_threw = true; return 0; }\n"
                + "static jobject f_alloc_object(JNIEnv *, jclass) { return BAD; }\n"
                + "static void f_call_nonvirtual_void(JNIEnv *, jobject, jclass, jmethodID,"
                + " va_list) {}\n"
                + "namespace native_jvm {\n"
                + "namespace utils {\n"
                + "jobject get_classloader_from_class(JNIEnv *, jclass) { return CLASSLOADER; }\n"
                + "jclass find_class_wo_static(JNIEnv *, jobject, jstring) { return RESOLVED; }\n"
                + "jobject get_lookup(JNIEnv *, jclass) { return LOOKUP; }\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int) { g_threw = true; }\n"
                + "}\n"
                + "namespace classes { namespace concatns {\n"
                + "char string_pool_storage[4096] = {};\n"
                + "char *string_pool = string_pool_storage;\n"
                + "jstring cstrings[" + strings + "] = {};\n"
                + "std::mutex cclasses_mtx[" + classes + "];\n"
                + "jclass cclasses[" + classes + "] = {};\n"
                + "jmethodID cmethods[" + methods + "] = {};\n"
                + "jfieldID cfields[" + fields + "] = {};\n"
                + body
                + "} }\n"
                + "}\n"
                + "int main() {\n"
                + "    JNINativeInterface_ functions = {};\n"
                + "    functions.ExceptionCheck = f_exception_check;\n"
                + "    functions.IsSameObject = f_is_same;\n"
                + "    functions.NewWeakGlobalRef = f_new_weak;\n"
                + "    functions.DeleteLocalRef = f_delete_local;\n"
                + "    functions.NewObjectArray = f_new_object_array;\n"
                + "    functions.SetObjectArrayElement = f_set_object_array;\n"
                + "    functions.GetStaticMethodID = f_get_static_mid;\n"
                + "    functions.GetMethodID = f_get_mid;\n"
                + "    functions.CallStaticObjectMethodV = f_call_static_object;\n"
                + "    functions.CallObjectMethodV = f_call_object;\n"
                + "    functions.IsInstanceOf = f_is_instance_of;\n"
                + "    functions.ExceptionOccurred = f_exception_occurred;\n"
                + "    functions.ExceptionClear = f_exception_clear;\n"
                + "    functions.Throw = f_throw;\n"
                + "    functions.AllocObject = f_alloc_object;\n"
                + "    functions.CallNonvirtualVoidMethodV = f_call_nonvirtual_void;\n"
                + "    JNIEnv_ env_value = {};\n"
                + "    env_value.functions = &functions;\n"
                + "    JNIEnv *env = &env_value;\n"
                + "    jclass clazz = reinterpret_cast<jclass>(0x9001);\n"
                + "    jobject arg0 = reinterpret_cast<jobject>(0x9002);\n"
                + "    jobject result = native_jvm::classes::concatns::" + function
                + "(env, clazz, arg0);\n"
                + "    if (g_threw) return 2;\n"
                + "    if (g_call_static != 2) return 3;\n"
                + "    if (g_call_object != 2) return 4;\n"
                + "    if (result != RESULT) return 1;\n"
                + "    return 0;\n"
                + "}\n";

        Path directory = Files.createTempDirectory("ir-indy-run");
        Path sourceFile = directory.resolve("indy.cpp");
        Path binary = directory.resolve("indy");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process compileProcess = new ProcessBuilder(gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                sourceFile.toString(), "-o", binary.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int compileExit = compileProcess.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput),
                StandardCharsets.UTF_8);
        assertEquals(0, compileExit, "g++ failed:\n" + output + "\nSource:\n" + source);

        Process runProcess = new ProcessBuilder(binary.toString()).start();
        assertEquals(0, runProcess.waitFor(),
                "Lowered invokedynamic did not run the bootstrap and call-site target");
    }

    @Test
    public void rejectsUnsupportedAfterPhaseElevenInvokesBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseElevenInvokesMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void rejectsUnsupportedAfterPhaseFourteenOpsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseFourteenOpsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void lowersStringLdcThroughExistingModifiedUtf8StringPool() throws Exception {
        MethodNode method = stringLdcMethod();
        IrMethod ir = frontend.build("example/Math", method);
        List<IrNodes.StringConst> constants = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.StringConst.class::isInstance)
                .map(IrNodes.StringConst.class::cast)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("", "ascii", "héllo世界", "nul\0inside"),
                constants.stream().map(IrNodes.StringConst::getValue)
                        .collect(Collectors.toList()));
        assertTrue(constants.stream()
                .allMatch(constant -> constant.getResult().getType() == IrType.REFERENCE));
        assertTrue(ir.toString().contains("ldc_string \"nul\\0inside\""));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("= (jobject) cstrings["));
        assertTrue(cpp.contains("env->CallStaticIntMethod"));
        assertTrue(cpp.contains("env->GetStringLength"));
        for (IrNodes.StringConst constant : constants) {
            assertTrue(obfuscator.getCachedStrings().getCache()
                    .containsKey(constant.getValue()));
        }

        Path directory = Files.createTempDirectory("ir-ldc-string-pool");
        String cppFile;
        try (ClassSourceBuilder builder = new ClassSourceBuilder(
                directory, "example/Math", 0, obfuscator.getStringPool())) {
            builder.addHeader(obfuscator.getCachedStrings().size(),
                    obfuscator.getCachedClasses().size(),
                    obfuscator.getCachedMethods().size(),
                    obfuscator.getCachedFields().size());
            builder.addInstructions(context.output.toString());
            builder.registerMethods(obfuscator.getCachedStrings(),
                    obfuscator.getCachedClasses(), context.nativeMethods.toString(),
                    Collections.emptyList());
            cppFile = builder.getCppFilename();
        }
        String generated = new String(Files.readAllBytes(directory.resolve(cppFile)),
                StandardCharsets.UTF_8);
        assertEquals(obfuscator.getCachedStrings().size(),
                countOccurrences(generated, "env->NewStringUTF("));
        assertTrue(generated.contains("env->NewStringUTF(((char *)(string_pool + "));
        assertTrue(obfuscator.getStringPool().build().contains("-64, -128"),
                "embedded NUL must use the modified-UTF-8 C0 80 encoding");
    }

    @Test
    public void lowersObjectAndArrayClassLdcThroughExistingClassCache() {
        MethodNode method = classLdcMethod();
        IrMethod ir = frontend.build("example/Math", method);
        List<IrNodes.ClassConst> constants = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.ClassConst.class::isInstance)
                .map(IrNodes.ClassConst.class::cast)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList("java/lang/String", "example/Fixture", "[I",
                        "[Ljava/lang/String;"),
                constants.stream().map(IrNodes.ClassConst::getClassName)
                        .collect(Collectors.toList()));
        assertTrue(constants.stream()
                .allMatch(constant -> constant.getResult().getType() == IrType.REFERENCE));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertEquals(4, obfuscator.getCachedClasses().size());
        assertEquals(2, countOccurrences(cpp, "env->FindClass("));
        assertTrue(cpp.contains("utils::find_class_wo_static"));
        assertTrue(cpp.contains("= (jobject) cclasses["));
        int arrayLookup = cpp.indexOf("env->FindClass(");
        int pendingException = cpp.indexOf("env->ExceptionCheck()", arrayLookup);
        int materialization = cpp.indexOf("= (jobject) cclasses[", arrayLookup);
        assertTrue(arrayLookup >= 0 && pendingException > arrayLookup
                && materialization > pendingException);
        assertFalse(cpp.contains("env->ExceptionClear()"));
    }

    @Test
    public void lowersWideLongLdcThroughLongConst() {
        IrMethod ir = frontend.build("example/Math", longLdcMethod());
        List<IrNodes.LongConst> constants = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.LongConst.class::isInstance)
                .map(IrNodes.LongConst.class::cast)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(0x1_0000_0000L, -1L),
                constants.stream().map(IrNodes.LongConst::getValue)
                        .collect(Collectors.toList()));
        assertTrue(constants.stream()
                .allMatch(constant -> constant.getResult().getType() == IrType.I64));

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("4294967296LL"));
        assertTrue(cpp.contains("-1LL"));
    }

    @Test
    public void lowersEveryPrimitiveClassLdcThroughWrapperTypeFieldOnPrivateCopy() {
        MethodNode method = primitiveClassLdcMethod();
        List<Type> expectedTypes = Arrays.asList(
                Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.DOUBLE_TYPE,
                Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.SHORT_TYPE, Type.CHAR_TYPE,
                Type.VOID_TYPE);
        IrMethod ir = frontend.build("example/Math", method);
        List<IrNodes.GetStaticField> fields = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.GetStaticField.class::isInstance)
                .map(IrNodes.GetStaticField.class::cast)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(
                        "java/lang/Integer", "java/lang/Long", "java/lang/Float",
                        "java/lang/Double", "java/lang/Boolean", "java/lang/Byte",
                        "java/lang/Short", "java/lang/Character", "java/lang/Void"),
                fields.stream().map(IrNodes.GetStaticField::getOwner)
                        .collect(Collectors.toList()));
        assertTrue(fields.stream().allMatch(field ->
                "TYPE".equals(field.getName())
                        && "Ljava/lang/Class;".equals(field.getDescriptor())
                        && field.getResult().getType() == IrType.REFERENCE));

        List<Type> originalConstants = Arrays.stream(method.instructions.toArray())
                .filter(LdcInsnNode.class::isInstance)
                .map(LdcInsnNode.class::cast)
                .map(instruction -> (Type) instruction.cst)
                .collect(Collectors.toList());
        assertEquals(expectedTypes, originalConstants);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertEquals(9, countOccurrences(cpp, "env->GetStaticObjectField("));
        assertEquals(9, obfuscator.getCachedClasses().size());
        assertEquals(9, obfuscator.getCachedFields().size());
        assertFalse(cpp.contains("native.magic"));
        assertFalse(cpp.contains("native/magic"));
    }

    @Test
    public void primitiveClassLdcCompilesAndRunsWithHotSpotParity()
            throws Exception {
        assertTrue(executableOnPath("cmake") != null,
                "cmake is required for the primitive Class LDC runtime test");
        assertTrue(executableOnPath("g++") != null,
                "g++ is required for the primitive Class LDC runtime test");

        String ownerName = "example/PrimitiveClassLdcRuntime";
        Path directory = Files.createTempDirectory("ir-primitive-class-ldc-run");
        Path inputJar = directory.resolve("primitive-class-ldc.jar");
        Path referenceJar = directory.resolve("primitive-class-reference.jar");
        Path outputDirectory = directory.resolve("output");
        createPrimitiveClassLdcJar(inputJar, ownerName, true);
        createPrimitiveClassLdcJar(referenceJar, ownerName, false);

        ProcessHelper.ProcessResult javaResult = ProcessHelper.run(
                directory, 120_000,
                Arrays.asList(javaExecutable().toString(), "-Xverify:all",
                        "-jar", referenceJar.toString()));
        javaResult.check("plain primitive Class reference Java run");
        assertEquals(
                "true" + System.lineSeparator()
                        + "int" + System.lineSeparator()
                        + "true" + System.lineSeparator()
                        + "long" + System.lineSeparator()
                        + "true" + System.lineSeparator()
                        + "boolean" + System.lineSeparator()
                        + "true" + System.lineSeparator()
                        + "void" + System.lineSeparator(),
                javaResult.stdout);

        new NativeObfuscator().process(
                inputJar, outputDirectory, Collections.emptyList(),
                Collections.singletonList(
                        ownerName + "#main!([Ljava/lang/String;)V"),
                null, "native_library", null, Platform.STD_JAVA,
                false, false, CodegenMode.IR);

        Path outputJar = outputDirectory.resolve(inputJar.getFileName());
        ClassNode transformed = new ClassNode(Opcodes.ASM9);
        try (JarFile jar = new JarFile(outputJar.toFile())) {
            new org.objectweb.asm.ClassReader(jar.getInputStream(
                    jar.getJarEntry(ownerName + ".class"))).accept(transformed, 0);
        }
        for (String methodName : Arrays.asList(
                "primitiveInt", "primitiveLong", "primitiveBoolean", "primitiveVoid")) {
            MethodNode accessor = transformed.methods.stream()
                    .filter(method -> methodName.equals(method.name))
                    .findFirst().orElseThrow(AssertionError::new);
            assertTrue((accessor.access & Opcodes.ACC_NATIVE) != 0);
        }

        Path cppDirectory = outputDirectory.resolve("cpp");
        ProcessHelper.run(cppDirectory, 120_000,
                        Arrays.asList("cmake", "-DCMAKE_BUILD_TYPE=Release", "."))
                .check("primitive Class LDC CMake configure");
        ProcessHelper.run(cppDirectory, 160_000,
                        Arrays.asList("cmake", "--build", ".", "--config", "Release"))
                .check("primitive Class LDC CMake build");

        Path library;
        try (Stream<Path> files = Files.list(cppDirectory.resolve("build/lib"))) {
            library = files.filter(Files::isRegularFile)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Primitive Class LDC native library was not produced"));
        }
        Files.copy(library, outputDirectory.resolve(library.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);

        ProcessHelper.ProcessResult nativeResult = ProcessHelper.run(
                outputDirectory, 120_000,
                Arrays.asList(javaExecutable().toString(),
                        "-Xverify:all", "-Xcheck:jni",
                        "-Djava.library.path=" + outputDirectory,
                        "-jar", outputJar.toString()));
        nativeResult.check("native primitive Class LDC Java run");
        assertEquals(javaResult.stdout, nativeResult.stdout);
    }

    @Test
    public void admitsRawMethodHandleAndMethodTypeLdcOnPrivateCopy() {
        MethodNode method = rawMethodConstantsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                .processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("utils::get_lookup(env, clazz)"));
        assertTrue(cpp.contains("env->CallStaticObjectMethod"));
        assertTrue(cpp.contains("env->CallObjectMethod"));
        assertFalse(cpp.contains("native.magic"));
        assertFalse(cpp.contains("native/magic"));
    }

    @Test
    public void returnsAllocatedObjectWithReferenceCarrier() {
        MethodNode method = returnAllocatedObjectMethod();
        IrMethod ir = frontend.build("example/Math", method);

        assertEquals(IrType.REFERENCE, ir.getReturnType());
        assertTrue(ir.toString().contains(" = new java/lang/Object"));
        IrNodes.Return returnTerminator = ir.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.Return.class::isInstance)
                .map(IrNodes.Return.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrType.REFERENCE, returnTerminator.getValue().getType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        int allocation = cpp.indexOf("env->AllocObject(cclasses[");
        assertTrue(cpp.contains("jobject JNICALL __ngen_native_returnAllocatedObject0"));
        assertTrue(allocation >= 0);
        assertTrue(cpp.indexOf("return nullptr;", allocation) > allocation);
        assertFalse(cpp.contains("env->ExceptionClear()"));
        assertTrue(cpp.indexOf("return v" + returnTerminator.getValue().getId() + ";",
                allocation) > allocation);
    }

    @Test
    public void returnsAllocatedArrayWithJniDescriptorCarrier() {
        MethodNode method = returnAllocatedObjectArrayMethod();
        IrMethod ir = frontend.build("example/Math", method);

        assertEquals(IrType.REFERENCE, ir.getReturnType());
        IrNodes.Return returnTerminator = ir.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.Return.class::isInstance)
                .map(IrNodes.Return.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrType.REFERENCE, returnTerminator.getValue().getType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("jarray JNICALL __ngen_native_returnAllocatedObjectArray0"));
        assertTrue(cpp.contains("return nullptr;"));
        assertTrue(cpp.contains("return (jarray) v"
                + returnTerminator.getValue().getId() + ";"));
    }

    @Test
    public void returnsTypedNullReference() {
        MethodNode method = returnNullMethod();
        IrMethod ir = frontend.build("example/Math", method);

        assertEquals(IrType.REFERENCE, ir.getReturnType());
        assertTrue(ir.getBlocks().get(0).getInstructions().get(0)
                instanceof IrNodes.NullReference);
        assertTrue(ir.toString().contains("%v0:ref = aconst_null"));

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("jobject v0;"));
        assertTrue(cpp.contains("v0 = nullptr;"));
        assertTrue(cpp.contains("return v0;"));
    }

    @Test
    public void lowersIfnullAndIfnonnullAsReferenceConditions() {
        MethodNode ifNullMethod = referenceNullBranchMethod("ifNull", Opcodes.IFNULL);
        IrMethod ifNull = frontend.build("example/Math", ifNullMethod);
        IrNodes.ReferenceBranch ifNullBranch = ifNull.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.ReferenceBranch.class::isInstance)
                .map(IrNodes.ReferenceBranch.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrNodes.ReferenceBranch.Condition.IS_NULL,
                ifNullBranch.getCondition());
        assertEquals(IrType.REFERENCE, ifNullBranch.getReference().getType());
        assertTrue(ifNull.toString().contains("branch ifnull %arg0"));
        assertTrue(emitter.emitBody(ifNull).contains("if (arg0 == nullptr) {"));

        MethodNode ifNonNullMethod =
                referenceNullBranchMethod("ifNonNull", Opcodes.IFNONNULL);
        IrMethod ifNonNull = frontend.build("example/Math", ifNonNullMethod);
        IrNodes.ReferenceBranch ifNonNullBranch = ifNonNull.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.ReferenceBranch.class::isInstance)
                .map(IrNodes.ReferenceBranch.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrNodes.ReferenceBranch.Condition.IS_NON_NULL,
                ifNonNullBranch.getCondition());
        assertEquals(IrType.REFERENCE, ifNonNullBranch.getReference().getType());
        assertTrue(ifNonNull.toString().contains("branch ifnonnull %arg0"));
        assertTrue(emitter.emitBody(ifNonNull).contains("if (arg0 != nullptr) {"));
    }

    @Test
    public void lowersIfAcmpAsReferenceCompareConditions() {
        MethodNode acmpEqMethod =
                referenceCompareBranchMethod("acmpEq", Opcodes.IF_ACMPEQ);
        IrMethod acmpEq = frontend.build("example/Math", acmpEqMethod);
        IrNodes.ReferenceCompareBranch acmpEqBranch = acmpEq.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.ReferenceCompareBranch.class::isInstance)
                .map(IrNodes.ReferenceCompareBranch.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrNodes.ReferenceCompareBranch.Condition.EQ,
                acmpEqBranch.getCondition());
        assertEquals(IrType.REFERENCE, acmpEqBranch.getLeft().getType());
        assertEquals(IrType.REFERENCE, acmpEqBranch.getRight().getType());
        assertTrue(acmpEq.toString().contains("branch if_acmpeq %arg0, %arg1"));
        String acmpEqCpp = emitter.emitBody(acmpEq);
        assertTrue(acmpEqCpp.contains("if (arg0 == arg1) {"));
        assertFalse(acmpEqCpp.contains("nullptr) {"));

        MethodNode acmpNeMethod =
                referenceCompareBranchMethod("acmpNe", Opcodes.IF_ACMPNE);
        IrMethod acmpNe = frontend.build("example/Math", acmpNeMethod);
        IrNodes.ReferenceCompareBranch acmpNeBranch = acmpNe.getBlocks().stream()
                .map(IrBlock::getTerminator)
                .filter(IrNodes.ReferenceCompareBranch.class::isInstance)
                .map(IrNodes.ReferenceCompareBranch.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrNodes.ReferenceCompareBranch.Condition.NE,
                acmpNeBranch.getCondition());
        assertEquals(IrType.REFERENCE, acmpNeBranch.getLeft().getType());
        assertEquals(IrType.REFERENCE, acmpNeBranch.getRight().getType());
        assertTrue(acmpNe.toString().contains("branch if_acmpne %arg0, %arg1"));
        String acmpNeCpp = emitter.emitBody(acmpNe);
        assertTrue(acmpNeCpp.contains("if (arg0 != arg1) {"));
        assertFalse(acmpNeCpp.contains("nullptr) {"));
    }

    @Test
    public void executesReferenceCompareSemanticsWhenToolchainAvailable() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR IF_ACMP runtime test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR IF_ACMP runtime test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR IF_ACMP runtime test");

        String acmpEqBody = emitter.emitBody(
                frontend.build("example/Math",
                        referenceCompareBranchMethod("acmpEq", Opcodes.IF_ACMPEQ)));
        String acmpNeBody = emitter.emitBody(
                frontend.build("example/Math",
                        referenceCompareBranchMethod("acmpNe", Opcodes.IF_ACMPNE)));
        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "static jint acmpeq(jobject arg0, jobject arg1) {\n"
                + acmpEqBody
                + "}\n"
                + "static jint acmpne(jobject arg0, jobject arg1) {\n"
                + acmpNeBody
                + "}\n"
                + "int main() {\n"
                + "    jobject a = reinterpret_cast<jobject>(0x1000);\n"
                + "    jobject b = reinterpret_cast<jobject>(0x2000);\n"
                + "    jobject n = nullptr;\n"
                // IF_ACMPEQ: 1 iff the same object, both null included.
                + "    if (acmpeq(a, a) != 1) return 1;\n"
                + "    if (acmpeq(a, b) != 0) return 2;\n"
                + "    if (acmpeq(n, n) != 1) return 3;\n"
                + "    if (acmpeq(a, n) != 0) return 4;\n"
                // IF_ACMPNE: complement of the above.
                + "    if (acmpne(a, a) != 0) return 5;\n"
                + "    if (acmpne(a, b) != 1) return 6;\n"
                + "    if (acmpne(n, n) != 0) return 7;\n"
                + "    if (acmpne(a, n) != 1) return 8;\n"
                + "    return 0;\n"
                + "}\n";

        Path directory = Files.createTempDirectory("ir-acmp-run");
        Path sourceFile = directory.resolve("acmp.cpp");
        Path binary = directory.resolve("acmp");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process compileProcess = new ProcessBuilder(gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                sourceFile.toString(), "-o", binary.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int compileExit = compileProcess.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput),
                StandardCharsets.UTF_8);
        assertEquals(0, compileExit, "g++ failed:\n" + output + "\nSource:\n" + source);

        Process runProcess = new ProcessBuilder(binary.toString()).start();
        assertEquals(0, runProcess.waitFor(),
                "Generated IF_ACMP lowering took a wrong identity branch");
    }

    @Test
    public void popsUnusedCategoryOneInvokeResult() {
        MethodNode method = popUnusedCategoryOneInvokeResultMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertEquals(IrType.VOID, ir.getReturnType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->CallStaticIntMethod"));
        assertTrue(cpp.contains("return;"));
    }

    @Test
    public void rejectsPopOfCategoryTwoValue() {
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> frontend.build("example/Math", popLongMethod()));

        assertEquals(Opcodes.POP, error.getOpcode());
        assertTrue(error.getMessage().contains("category-one"));
    }

    @Test
    public void rejectsUnsupportedAfterPhaseNineOpsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseNineOpsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void rejectsUnsupportedInstructionAfterNewBeforeMutation() {
        MethodNode method = unsupportedAfterNewMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.POP2, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void lowersStaticIntFieldsThroughExistingCacheShape() {
        MethodNode method = staticIntFieldMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.toString().contains("putstatic example/Math.counter:I"));
        assertTrue(ir.toString().contains("getstatic example/Math.counter:I"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->GetStaticFieldID"));
        assertTrue(cpp.contains("env->SetStaticIntField"));
        assertTrue(cpp.contains("env->GetStaticIntField"));
        assertEquals(1, obfuscator.getCachedFields().size());
    }

    @Test
    public void lowersInstanceIntLongReferenceAndArrayFieldRoundTrips() {
        assertFieldRoundTrip(instanceFieldRoundTripMethod(
                        "instanceIntField", "I", Opcodes.ILOAD, Opcodes.IRETURN),
                IrType.I32, "GetIntField", "SetIntField");
        assertFieldRoundTrip(instanceFieldRoundTripMethod(
                        "instanceLongField", "J", Opcodes.LLOAD, Opcodes.LRETURN),
                IrType.I64, "GetLongField", "SetLongField");
        String objectCpp = assertFieldRoundTrip(instanceFieldRoundTripMethod(
                        "instanceObjectField", "Ljava/lang/Object;",
                        Opcodes.ALOAD, Opcodes.ARETURN),
                IrType.REFERENCE, "GetObjectField", "SetObjectField");
        String arrayCpp = assertFieldRoundTrip(instanceFieldRoundTripMethod(
                        "instanceArrayField", "[I", Opcodes.ALOAD, Opcodes.ARETURN),
                IrType.REFERENCE, "GetObjectField", "SetObjectField");

        assertFalse(objectCpp.contains("GetIntField"));
        assertFalse(objectCpp.contains("SetIntField"));
        assertTrue(arrayCpp.contains("return (jarray) v"));
    }

    @Test
    public void lowersStaticIntLongReferenceAndArrayFieldRoundTrips() {
        assertFieldRoundTrip(staticFieldRoundTripMethod(
                        "staticIntField", "I", Opcodes.ILOAD, Opcodes.IRETURN),
                IrType.I32, "GetStaticIntField", "SetStaticIntField");
        assertFieldRoundTrip(staticFieldRoundTripMethod(
                        "staticLongField", "J", Opcodes.LLOAD, Opcodes.LRETURN),
                IrType.I64, "GetStaticLongField", "SetStaticLongField");
        String objectCpp = assertFieldRoundTrip(staticFieldRoundTripMethod(
                        "staticObjectField", "Ljava/lang/Object;",
                        Opcodes.ALOAD, Opcodes.ARETURN),
                IrType.REFERENCE, "GetStaticObjectField", "SetStaticObjectField");
        String arrayCpp = assertFieldRoundTrip(staticFieldRoundTripMethod(
                        "staticArrayField", "[I", Opcodes.ALOAD, Opcodes.ARETURN),
                IrType.REFERENCE, "GetStaticObjectField", "SetStaticObjectField");

        assertFalse(objectCpp.contains("GetStaticIntField"));
        assertFalse(objectCpp.contains("SetStaticIntField"));
        assertTrue(arrayCpp.contains("return (jarray) v"));
    }

    @Test
    public void nullReceiverInstanceFieldAccessUsesExceptionalExit() {
        String getCpp = compileToCpp(nullReceiverGetFieldMethod(), 0);
        int getNullCheck = getCpp.indexOf("if (arg0 == nullptr)");
        int getThrow = getCpp.indexOf("utils::throw_re", getNullCheck);
        int getExit = getCpp.indexOf("return 0;", getThrow);
        assertTrue(getNullCheck >= 0 && getThrow > getNullCheck && getExit > getThrow);
        assertTrue(getCpp.contains("env->GetLongField"));
        assertFalse(getCpp.contains("env->ExceptionClear()"));

        String putCpp = compileToCpp(nullReceiverPutFieldMethod(), 0);
        int putNullCheck = putCpp.indexOf("if (arg0 == nullptr)");
        int putThrow = putCpp.indexOf("utils::throw_re", putNullCheck);
        int putExit = putCpp.indexOf("return;", putThrow);
        assertTrue(putNullCheck >= 0 && putThrow > putNullCheck && putExit > putThrow);
        assertTrue(putCpp.contains("env->SetObjectField"));
        assertFalse(putCpp.contains("env->ExceptionClear()"));
    }

    @Test
    public void nullReceiverSmallPrimitiveOpsUseExceptionalExit() {
        String fieldCpp = compileToCpp(instanceFieldRoundTripMethod(
                "nullableCharField", "C", Opcodes.ILOAD, Opcodes.IRETURN), 0);
        int fieldNullCheck = fieldCpp.indexOf("if (arg0 == nullptr)");
        int fieldThrow = fieldCpp.indexOf("utils::throw_re", fieldNullCheck);
        int fieldExit = fieldCpp.indexOf("return 0;", fieldThrow);
        int fieldCall = fieldCpp.indexOf("env->SetCharField", fieldExit);
        assertTrue(fieldNullCheck >= 0 && fieldThrow > fieldNullCheck
                && fieldExit > fieldThrow && fieldCall > fieldExit);

        String invokeCpp = compileToCpp(virtualPrimitiveInvokeMethod(
                "nullableBooleanInvoke", "identityBoolean", "(Z)Z"), 0);
        int invokeNullCheck = invokeCpp.indexOf("if (arg0 == nullptr)");
        int invokeThrow = invokeCpp.indexOf("utils::throw_re", invokeNullCheck);
        int invokeExit = invokeCpp.indexOf("return 0;", invokeThrow);
        int invokeCall = invokeCpp.indexOf("env->CallBooleanMethod", invokeExit);
        assertTrue(invokeNullCheck >= 0 && invokeThrow > invokeNullCheck
                && invokeExit > invokeThrow && invokeCall > invokeExit);
    }

    @Test
    public void nullReceiverFloatAndDoubleOpsUseExceptionalExit() {
        String fieldCpp = compileToCpp(instanceFieldRoundTripMethod(
                "nullableFloatField", "F", Opcodes.FLOAD, Opcodes.FRETURN), 0);
        int fieldNullCheck = fieldCpp.indexOf("if (arg0 == nullptr)");
        int fieldThrow = fieldCpp.indexOf("utils::throw_re", fieldNullCheck);
        int fieldExit = fieldCpp.indexOf("return 0;", fieldThrow);
        int fieldCall = fieldCpp.indexOf("env->SetFloatField", fieldExit);
        assertTrue(fieldNullCheck >= 0 && fieldThrow > fieldNullCheck
                && fieldExit > fieldThrow && fieldCall > fieldExit);

        String invokeCpp = compileToCpp(virtualPrimitiveInvokeMethod(
                "nullableDoubleInvoke", "identityDouble", "(D)D"), 0);
        int invokeNullCheck = invokeCpp.indexOf("if (arg0 == nullptr)");
        int invokeThrow = invokeCpp.indexOf("utils::throw_re", invokeNullCheck);
        int invokeExit = invokeCpp.indexOf("return 0;", invokeThrow);
        int invokeCall = invokeCpp.indexOf("env->CallDoubleMethod", invokeExit);
        assertTrue(invokeNullCheck >= 0 && invokeThrow > invokeNullCheck
                && invokeExit > invokeThrow && invokeCall > invokeExit);
    }

    @Test
    public void lowersSmallPrimitiveFieldRoundTripsWithJvmNarrowing() {
        assertSmallPrimitiveFieldRoundTrip("booleanFalse", "Z", "Boolean",
                "jboolean", 0, false);
        assertSmallPrimitiveFieldRoundTrip("booleanTrue", "Z", "Boolean",
                "jboolean", 1, false);
        assertSmallPrimitiveFieldRoundTrip("byteNegative", "B", "Byte",
                "jbyte", -7, false);
        assertSmallPrimitiveFieldRoundTrip("charAboveAscii", "C", "Char",
                "jchar", 200, false);
        assertSmallPrimitiveFieldRoundTrip("shortNegative", "S", "Short",
                "jshort", -300, false);

        assertSmallPrimitiveFieldRoundTrip("staticBooleanFalse", "Z", "Boolean",
                "jboolean", 0, true);
        assertSmallPrimitiveFieldRoundTrip("staticBooleanTrue", "Z", "Boolean",
                "jboolean", 1, true);
        assertSmallPrimitiveFieldRoundTrip("staticByteNegative", "B", "Byte",
                "jbyte", -7, true);
        assertSmallPrimitiveFieldRoundTrip("staticCharAboveAscii", "C", "Char",
                "jchar", 200, true);
        assertSmallPrimitiveFieldRoundTrip("staticShortNegative", "S", "Short",
                "jshort", -300, true);
    }

    @Test
    public void lowersFloatAndDoubleFieldRoundTripsWithExactBitsAndJniFamilies() {
        float[] floats = {
                -0.0f, 0.0f, Float.intBitsToFloat(0x7fc01234),
                Float.POSITIVE_INFINITY, -13.25f
        };
        for (int i = 0; i < floats.length; i++) {
            assertFloatingFieldConstant("float" + i, "F", IrType.F32,
                    "Float", floats[i], false);
            assertFloatingFieldConstant("staticFloat" + i, "F", IrType.F32,
                    "Float", floats[i], true);
        }

        double[] doubles = {
                -0.0d, 0.0d, Double.longBitsToDouble(0x7ff8000000001234L),
                Double.NEGATIVE_INFINITY, 9876.5d
        };
        for (int i = 0; i < doubles.length; i++) {
            assertFloatingFieldConstant("double" + i, "D", IrType.F64,
                    "Double", doubles[i], false);
            assertFloatingFieldConstant("staticDouble" + i, "D", IrType.F64,
                    "Double", doubles[i], true);
        }
    }

    @Test
    public void rejectsUnsupportedAfterPhaseTenFieldsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseTenFieldsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void emitsBitwiseAndShiftOps() {
        String cpp = emitter.emitBody(frontend.build("example/Math", bitwiseShiftMethod()));

        assertTrue(cpp.contains("(uint32_t) arg0 & (uint32_t) arg1"));
        assertTrue(cpp.contains("| (uint32_t) arg1"));
        assertTrue(cpp.contains("^ (uint32_t) arg1"));
        assertTrue(cpp.contains("<< ((uint32_t) arg1 & 31)"));
        assertTrue(cpp.contains("(int32_t) v2 >> ((uint32_t) arg1 & 31)")
                || cpp.contains(">> ((uint32_t) arg1 & 31)"));
    }

    @Test
    public void emitsUnaryIntConversions() {
        IrMethod ir = frontend.build("example/Math", unaryMethod());
        String pretty = ir.toString();
        assertTrue(pretty.contains("ineg"));
        assertTrue(pretty.contains("i2b"));
        assertTrue(pretty.contains("i2s"));
        assertTrue(pretty.contains("i2c"));

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("(jint) (-(uint32_t) arg0)"));
        assertTrue(cpp.contains("(jint) (jbyte)"));
        assertTrue(cpp.contains("(jint) (jshort)"));
        assertTrue(cpp.contains("(jint) (jchar)"));
    }

    @Test
    public void lowersFloatAndDoubleArithmeticRemainderNegationAndNanCompares() {
        for (boolean wide : new boolean[]{false, true}) {
            MethodNode method = floatingScalarOpsMethod(wide);
            IrMethod ir = frontend.build("example/Math", method);
            String prefix = wide ? "d" : "f";
            String pretty = ir.toString();
            assertTrue(pretty.contains(" = " + prefix + "add "));
            assertTrue(pretty.contains(" = " + prefix + "sub "));
            assertTrue(pretty.contains(" = " + prefix + "mul "));
            assertTrue(pretty.contains(" = " + prefix + "div "));
            assertTrue(pretty.contains(" = " + prefix + "rem "));
            assertTrue(pretty.contains(" = " + prefix + "neg "));
            assertTrue(pretty.contains(" = " + prefix + "cmpl "));
            assertTrue(pretty.contains(" = " + prefix + "cmpg "));

            String cpp = compileToCpp(method, 0);
            assertTrue(cpp.contains("std::fmod"));
            assertTrue(cpp.contains("std::isnan"));
            assertTrue(cpp.contains("? -1 :"));
            assertTrue(cpp.contains("? 1 :"));
            assertTrue(cpp.contains(" / "));
            assertFalse(cpp.contains("ArithmeticException"));
        }
    }

    @Test
    public void lowersAllFloatAndDoubleConversionsWithJvmSaturation() {
        int[] opcodes = {
                Opcodes.I2F, Opcodes.F2I, Opcodes.L2F, Opcodes.F2L,
                Opcodes.I2D, Opcodes.D2I, Opcodes.L2D, Opcodes.D2L,
                Opcodes.F2D, Opcodes.D2F
        };
        IrNodes.Conversion.Operation[] operations = {
                IrNodes.Conversion.Operation.I2F, IrNodes.Conversion.Operation.F2I,
                IrNodes.Conversion.Operation.L2F, IrNodes.Conversion.Operation.F2L,
                IrNodes.Conversion.Operation.I2D, IrNodes.Conversion.Operation.D2I,
                IrNodes.Conversion.Operation.L2D, IrNodes.Conversion.Operation.D2L,
                IrNodes.Conversion.Operation.F2D, IrNodes.Conversion.Operation.D2F
        };
        for (int i = 0; i < opcodes.length; i++) {
            IrMethod ir = frontend.build("example/Math", floatingConversionMethod(opcodes[i]));
            IrNodes.Conversion conversion = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.Conversion.class::isInstance)
                    .map(IrNodes.Conversion.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            assertEquals(operations[i], conversion.getOperation());
        }

        for (int opcode : new int[]{Opcodes.F2I, Opcodes.D2I}) {
            String cpp = emitter.emitBody(frontend.build(
                    "example/Math", floatingConversionMethod(opcode)));
            assertTrue(cpp.contains("std::isnan"));
            assertTrue(cpp.contains("2147483647"));
            assertTrue(cpp.contains("0x80000000U"));
            assertTrue(cpp.contains("? 0 :"));
        }
        for (int opcode : new int[]{Opcodes.F2L, Opcodes.D2L}) {
            String cpp = emitter.emitBody(frontend.build(
                    "example/Math", floatingConversionMethod(opcode)));
            assertTrue(cpp.contains("std::isnan"));
            assertTrue(cpp.contains("9223372036854775807LL"));
            assertTrue(cpp.contains("0x8000000000000000ULL"));
            assertTrue(cpp.contains("? 0 :"));
        }
    }

    @Test
    public void carriesFloatAndCategoryTwoDoubleThroughStackPhis() {
        IrMethod floatIr = frontend.build("example/Math", floatingStackPhiMethod(false));
        IrPhi floatPhi = onlyStackPhi(floatIr);
        assertEquals(IrType.F32, floatPhi.getResult().getType());
        assertEquals(0, floatPhi.getSlotIndex());
        assertTrue(emitter.emitBody(floatIr).contains("jfloat"));

        IrMethod doubleIr = frontend.build("example/Math", floatingStackPhiMethod(true));
        IrPhi doublePhi = onlyStackPhi(doubleIr);
        assertEquals(IrType.F64, doublePhi.getResult().getType());
        assertEquals(0, doublePhi.getSlotIndex());
        assertTrue(emitter.emitBody(doubleIr).contains("jdouble"));
    }

    @Test
    public void swapsEveryI32F32AndReferenceCategoryOnePairAsSsaValues() {
        Type[] categoryOne = {
                Type.INT_TYPE, Type.FLOAT_TYPE, Type.getType(Object.class)
        };
        for (Type first : categoryOne) {
            for (Type second : categoryOne) {
                MethodNode method = swapMethod(first, second);
                IrMethod ir = frontend.build("example/Math", method);
                String cpp = emitter.emitBody(ir);

                assertTrue(cpp.contains("return arg1;"),
                        first + "/" + second + " did not preserve the reordered value");
                assertFalse(cpp.contains("env->"),
                        "SWAP must not emit JNI calls for " + first + "/" + second);
                assertFalse(ir.toString().contains(" = swap "),
                        "SWAP should disappear into the SSA stack order");
            }
        }
    }

    @Test
    public void rejectsSwapWhenEitherOperandIsLongOrDoubleBeforeMutation() {
        Type reference = Type.getType(Object.class);
        Type[][] pairs = {
                {Type.LONG_TYPE, Type.INT_TYPE},
                {Type.INT_TYPE, Type.LONG_TYPE},
                {Type.DOUBLE_TYPE, reference},
                {reference, Type.DOUBLE_TYPE}
        };
        for (Type[] pair : pairs) {
            MethodNode method = invalidSwapMethod(pair[0], pair[1]);
            NativeObfuscator obfuscator = new NativeObfuscator();
            MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

            UnsupportedIrConstructException error = assertThrows(
                    UnsupportedIrConstructException.class,
                    () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                            .processMethod(context));

            assertEquals(Opcodes.SWAP, error.getOpcode());
            assertTrue(error.getMessage().contains("category-one"));
            assertUnchangedAfterRejectedIr(method, context, obfuscator);
        }
    }

    @Test
    public void lowersIntArrayLoadStoreAndLength() {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, intArrayMethod(), 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->GetArrayLength"));
        assertTrue(cpp.contains("env->GetIntArrayRegion"));
        assertTrue(cpp.contains("env->SetIntArrayRegion"));
        assertTrue(cpp.contains("utils::throw_re"));
    }

    @Test
    public void lowersObjectAndStringArrayRoundTripsThroughObjectArrayJni() {
        MethodNode[] methods = {
                referenceArrayRoundTripMethod(false),
                referenceArrayRoundTripMethod(true)
        };
        for (MethodNode method : methods) {
            IrMethod ir = frontend.build("example/Math", method);
            IrNodes.ArrayStore store = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.ArrayStore.class::isInstance)
                    .map(IrNodes.ArrayStore.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            IrNodes.ArrayLoad load = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.ArrayLoad.class::isInstance)
                    .map(IrNodes.ArrayLoad.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            assertEquals(IrType.REFERENCE, store.getElementType());
            assertEquals(IrType.REFERENCE, load.getElementType());
            assertTrue(ir.toString().contains("aastore"));
            assertTrue(ir.toString().contains("aaload"));

            String cpp = compileToCpp(method, 0);
            int storeCall = cpp.indexOf("env->SetObjectArrayElement");
            int storeCheck = cpp.indexOf("env->ExceptionCheck()", storeCall);
            int loadCall = cpp.indexOf("env->GetObjectArrayElement");
            int loadCheck = cpp.indexOf("env->ExceptionCheck()", loadCall);
            int loadedValue = cpp.indexOf(" = aaload", loadCheck);
            assertTrue(storeCall >= 0 && storeCheck > storeCall);
            assertTrue(loadCall > storeCheck && loadCheck > loadCall && loadedValue > loadCheck);
            assertFalse(cpp.contains("GetIntArrayRegion"));
            assertFalse(cpp.contains("SetIntArrayRegion"));
        }
    }

    @Test
    public void routesReferenceArrayNullBoundsAndStoreTypeFailures() {
        MethodNode nullLoad = nullReferenceArrayLoadCatchMethod();
        IrMethod nullIr = frontend.build("example/Math", nullLoad);
        assertArrayExceptionEdge(nullIr, IrNodes.ArrayLoad.class,
                "java/lang/NullPointerException");
        String nullCpp = compileToCpp(nullLoad, 0);
        int nullThrow = nullCpp.indexOf("utils::throw_re");
        int nullDispatch = nullCpp.indexOf("goto IR_CATCH_0;", nullThrow);
        int nullLoadCall = nullCpp.indexOf("env->GetObjectArrayElement");
        assertTrue(nullThrow >= 0 && nullDispatch > nullThrow && nullLoadCall > nullDispatch);

        for (boolean upperBound : new boolean[]{false, true}) {
            MethodNode boundsLoad = referenceArrayBoundsCatchMethod(upperBound);
            IrMethod boundsIr = frontend.build("example/Math", boundsLoad);
            assertArrayExceptionEdge(boundsIr, IrNodes.ArrayLoad.class,
                    "java/lang/ArrayIndexOutOfBoundsException");
            String boundsCpp = compileToCpp(boundsLoad, 1);
            int boundsCall = boundsCpp.indexOf("env->GetObjectArrayElement");
            int boundsCheck = boundsCpp.indexOf("env->ExceptionCheck()", boundsCall);
            int boundsDispatch = boundsCpp.indexOf("goto IR_CATCH_0;", boundsCheck);
            assertTrue(boundsCall >= 0 && boundsCheck > boundsCall
                    && boundsDispatch > boundsCheck);
        }

        MethodNode badStore = wrongTypeReferenceArrayStoreCatchMethod();
        IrMethod storeIr = frontend.build("example/Math", badStore);
        assertArrayExceptionEdge(storeIr, IrNodes.ArrayStore.class,
                "java/lang/ArrayStoreException");
        String storeCpp = compileToCpp(badStore, 2);
        int storeCall = storeCpp.indexOf("env->SetObjectArrayElement");
        int storeCheck = storeCpp.indexOf("env->ExceptionCheck()", storeCall);
        int storeDispatch = storeCpp.indexOf("goto IR_CATCH_0;", storeCheck);
        assertTrue(storeCall >= 0 && storeCheck > storeCall && storeDispatch > storeCheck);
        assertFalse(storeCpp.substring(storeCall, storeDispatch)
                .contains("ExceptionClear"));
    }

    @Test
    public void rejectsUnsupportedAfterPhaseSixteenOpsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseSixteenOpsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void lowersEveryLegalWideStackShuffleFormAsSsaReordering() {
        Type object = Type.getType(Object.class);

        assertStackShuffle("dup2Form1IntFloat", Opcodes.DUP2,
                new Type[]{Type.INT_TYPE, Type.FLOAT_TYPE}, 0, 1, 0, 1);
        assertStackShuffle("dup2Form1ReferenceInt", Opcodes.DUP2,
                new Type[]{object, Type.INT_TYPE}, 0, 1, 0, 1);
        assertStackShuffle("dup2Form2Long", Opcodes.DUP2,
                new Type[]{Type.LONG_TYPE}, 0, 0);
        assertStackShuffle("dup2Form2Double", Opcodes.DUP2,
                new Type[]{Type.DOUBLE_TYPE}, 0, 0);

        assertStackShuffle("dupX2Form1", Opcodes.DUP_X2,
                new Type[]{object, Type.INT_TYPE, Type.FLOAT_TYPE}, 2, 0, 1, 2);
        assertStackShuffle("dupX2Form2Long", Opcodes.DUP_X2,
                new Type[]{Type.LONG_TYPE, object}, 1, 0, 1);
        assertStackShuffle("dupX2Form2Double", Opcodes.DUP_X2,
                new Type[]{Type.DOUBLE_TYPE, Type.FLOAT_TYPE}, 1, 0, 1);

        assertStackShuffle("dup2X1Form1", Opcodes.DUP2_X1,
                new Type[]{Type.FLOAT_TYPE, object, Type.INT_TYPE}, 1, 2, 0, 1, 2);
        assertStackShuffle("dup2X1Form2Long", Opcodes.DUP2_X1,
                new Type[]{object, Type.LONG_TYPE}, 1, 0, 1);
        assertStackShuffle("dup2X1Form2Double", Opcodes.DUP2_X1,
                new Type[]{Type.INT_TYPE, Type.DOUBLE_TYPE}, 1, 0, 1);

        assertStackShuffle("dup2X2Form1", Opcodes.DUP2_X2,
                new Type[]{Type.INT_TYPE, Type.FLOAT_TYPE, object, Type.INT_TYPE},
                2, 3, 0, 1, 2, 3);
        assertStackShuffle("dup2X2Form2Long", Opcodes.DUP2_X2,
                new Type[]{Type.FLOAT_TYPE, object, Type.LONG_TYPE}, 2, 0, 1, 2);
        assertStackShuffle("dup2X2Form2Double", Opcodes.DUP2_X2,
                new Type[]{object, Type.INT_TYPE, Type.DOUBLE_TYPE}, 2, 0, 1, 2);
        assertStackShuffle("dup2X2Form3Long", Opcodes.DUP2_X2,
                new Type[]{Type.LONG_TYPE, object, Type.FLOAT_TYPE}, 1, 2, 0, 1, 2);
        assertStackShuffle("dup2X2Form3Double", Opcodes.DUP2_X2,
                new Type[]{Type.DOUBLE_TYPE, Type.INT_TYPE, object}, 1, 2, 0, 1, 2);
        assertStackShuffle("dup2X2Form4LongDouble", Opcodes.DUP2_X2,
                new Type[]{Type.LONG_TYPE, Type.DOUBLE_TYPE}, 1, 0, 1);
        assertStackShuffle("dup2X2Form4DoubleLong", Opcodes.DUP2_X2,
                new Type[]{Type.DOUBLE_TYPE, Type.LONG_TYPE}, 1, 0, 1);

        assertStackShuffle("pop2Form1IntFloat", Opcodes.POP2,
                new Type[]{object, Type.INT_TYPE, Type.FLOAT_TYPE}, 0);
        assertStackShuffle("pop2Form1ReferenceInt", Opcodes.POP2,
                new Type[]{Type.FLOAT_TYPE, object, Type.INT_TYPE}, 0);
        assertStackShuffle("pop2Form2Long", Opcodes.POP2,
                new Type[]{Type.INT_TYPE, Type.LONG_TYPE}, 0);
        assertStackShuffle("pop2Form2Double", Opcodes.POP2,
                new Type[]{Type.FLOAT_TYPE, Type.DOUBLE_TYPE}, 0);
    }

    @Test
    public void rejectsIllegalWideStackShuffleMixesBeforeMutation() {
        Type object = Type.getType(Object.class);
        assertIllegalStackShuffle(Opcodes.DUP2,
                Type.LONG_TYPE, Type.INT_TYPE);
        assertIllegalStackShuffle(Opcodes.DUP_X2,
                object, Type.DOUBLE_TYPE);
        assertIllegalStackShuffle(Opcodes.DUP2_X1,
                Type.LONG_TYPE, Type.FLOAT_TYPE, object);
        assertIllegalStackShuffle(Opcodes.DUP2_X1,
                Type.DOUBLE_TYPE, Type.LONG_TYPE);
        assertIllegalStackShuffle(Opcodes.DUP2_X2,
                Type.LONG_TYPE, Type.INT_TYPE);
        assertIllegalStackShuffle(Opcodes.DUP2_X2,
                Type.DOUBLE_TYPE, object, Type.LONG_TYPE);
        assertIllegalStackShuffle(Opcodes.POP2,
                Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
    }

    @Test
    public void compilesSwapThenDup2X1MeasuredPattern() {
        Type object = Type.getType(Object.class);
        assertStackShuffle("swapThenDup2X1",
                new int[]{Opcodes.SWAP, Opcodes.DUP2_X1},
                new Type[]{object, Type.INT_TYPE, Type.FLOAT_TYPE},
                2, 1, 0, 2, 1);
    }

    @Test
    public void rejectsUnsupportedAfterPhaseSeventeenStackOpsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseSeventeenStackOpsMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void lowersIntNewArrayAndRoutesFailuresToCatchDispatch() {
        MethodNode method = newIntArrayCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.toString().contains("newarray int"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        int allocation = cpp.indexOf("env->NewIntArray(arg0)");
        int nullCheck = cpp.indexOf("== nullptr", allocation);
        int exceptionCheck = cpp.indexOf("env->ExceptionCheck()", allocation);
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", allocation);
        assertTrue(allocation >= 0 && nullCheck > allocation && exceptionCheck > allocation
                && dispatch > exceptionCheck);
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->Throw(caught_exception);"));
    }

    @Test
    public void lowersEveryPrimitiveNewArrayAndLoadStoreRoundTripWithExactJniFamily() {
        int[] atypes = {
                Opcodes.T_BOOLEAN, Opcodes.T_BYTE, Opcodes.T_CHAR, Opcodes.T_SHORT,
                Opcodes.T_INT, Opcodes.T_FLOAT, Opcodes.T_LONG, Opcodes.T_DOUBLE
        };
        Type[] elementTypes = {
                Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.CHAR_TYPE, Type.SHORT_TYPE,
                Type.INT_TYPE, Type.FLOAT_TYPE, Type.LONG_TYPE, Type.DOUBLE_TYPE
        };
        IrNodes.ArrayType[] arrayTypes = {
                IrNodes.ArrayType.BOOLEAN, IrNodes.ArrayType.BYTE,
                IrNodes.ArrayType.CHAR, IrNodes.ArrayType.SHORT,
                IrNodes.ArrayType.INT, IrNodes.ArrayType.FLOAT,
                IrNodes.ArrayType.LONG, IrNodes.ArrayType.DOUBLE
        };
        String[] carriers = {
                "Boolean", "Byte", "Char", "Short", "Int", "Float", "Long", "Double"
        };

        for (int i = 0; i < atypes.length; i++) {
            MethodNode method = primitiveArrayRoundTripMethod(
                    "roundTrip" + carriers[i], atypes[i], elementTypes[i]);
            IrMethod ir = frontend.build("example/Math", method);
            IrNodes.NewArray allocation = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.NewArray.class::isInstance)
                    .map(IrNodes.NewArray.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            IrNodes.ArrayStore store = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.ArrayStore.class::isInstance)
                    .map(IrNodes.ArrayStore.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            IrNodes.ArrayLoad load = ir.getBlocks().stream()
                    .flatMap(block -> block.getInstructions().stream())
                    .filter(IrNodes.ArrayLoad.class::isInstance)
                    .map(IrNodes.ArrayLoad.class::cast)
                    .findFirst().orElseThrow(AssertionError::new);
            assertEquals(arrayTypes[i], allocation.getArrayType());
            assertEquals(arrayTypes[i], store.getArrayType());
            assertEquals(arrayTypes[i], load.getArrayType());
            assertEquals(arrayTypes[i].getElementType(), load.getResult().getType());

            String cpp = compileToCpp(method, i);
            assertTrue(cpp.contains("env->New" + carriers[i] + "Array(arg0)"));
            assertTrue(cpp.contains("env->Get" + carriers[i] + "ArrayRegion"));
            assertTrue(cpp.contains("env->Set" + carriers[i] + "ArrayRegion"));
            assertTrue(cpp.contains(arrayJniType(carriers[i])));
            assertFalse(cpp.contains("cstack"));
        }
    }

    @Test
    public void keepsBooleanAndByteArrayJniFamiliesDistinct() {
        String booleanCpp = compileToCpp(primitiveArrayRoundTripMethod(
                "booleanArray", Opcodes.T_BOOLEAN, Type.BOOLEAN_TYPE), 0);
        String byteCpp = compileToCpp(primitiveArrayRoundTripMethod(
                "byteArray", Opcodes.T_BYTE, Type.BYTE_TYPE), 1);

        assertTrue(booleanCpp.contains("NewBooleanArray"));
        assertTrue(booleanCpp.contains("GetBooleanArrayRegion"));
        assertTrue(booleanCpp.contains("SetBooleanArrayRegion"));
        assertFalse(booleanCpp.contains("ByteArray"));
        assertTrue(booleanCpp.contains("(uint32_t)"));
        assertTrue(booleanCpp.contains("& 1"));

        assertTrue(byteCpp.contains("NewByteArray"));
        assertTrue(byteCpp.contains("GetByteArrayRegion"));
        assertTrue(byteCpp.contains("SetByteArrayRegion"));
        assertFalse(byteCpp.contains("BooleanArray"));
        assertTrue(byteCpp.contains("(jbyte)"));
    }

    @Test
    public void rejectsNegativeLengthForEveryPrimitiveNewArrayWithPendingException() {
        int[] atypes = {
                Opcodes.T_BOOLEAN, Opcodes.T_BYTE, Opcodes.T_CHAR, Opcodes.T_SHORT,
                Opcodes.T_INT, Opcodes.T_FLOAT, Opcodes.T_LONG, Opcodes.T_DOUBLE
        };
        for (int i = 0; i < atypes.length; i++) {
            MethodNode method = newPrimitiveArrayCatchMethod(
                    "catchNegativePrimitive" + i, atypes[i]);
            String cpp = compileToCpp(method, i);
            int guard = cpp.indexOf("arg0 < 0");
            int throwCall = cpp.indexOf("utils::throw_re", guard);
            int dispatch = cpp.indexOf("goto IR_CATCH_0;", throwCall);
            int allocation = cpp.indexOf("env->New", dispatch);
            assertTrue(guard >= 0 && throwCall > guard && dispatch > throwCall
                    && allocation > dispatch);
            assertTrue(cpp.contains("java/lang/NegativeArraySizeException")
                    || cpp.contains("string_pool + "));
        }
    }

    @Test
    public void lowersRectangularPrimitiveAndReferenceMultianewarray() {
        String intCpp = compileToCpp(multiArrayMethod(
                "newIntMatrix", "[[I", 2), 0);
        assertTrue(intCpp.contains("utils::create_multidim_array_value<5>("));
        assertTrue(intCpp.contains("{ arg0, arg1 }"));
        assertTrue(intCpp.contains("return (jarray)"));

        String stringCpp = compileToCpp(multiArrayMethod(
                "newStringMatrix", "[[Ljava/lang/String;", 2), 1);
        assertTrue(stringCpp.contains("utils::create_multidim_array(env, classloader"));
        assertTrue(stringCpp.contains("{ arg0, arg1 }"));

        String[] primitiveDescriptors = {"[[Z", "[[B", "[[C", "[[S",
                "[[I", "[[F", "[[J", "[[D"};
        for (int i = 0; i < primitiveDescriptors.length; i++) {
            IrMethod ir = frontend.build("example/Math", multiArrayMethod(
                    "multiPrimitive" + i, primitiveDescriptors[i], 2));
            assertTrue(ir.toString().contains(
                    "multianewarray " + primitiveDescriptors[i]));
        }
    }

    @Test
    public void checksEveryMultianewarrayDimensionBeforeAllocation() {
        MethodNode method = multiArrayNegativeCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertArrayExceptionEdge(ir, IrNodes.MultiNewArray.class,
                "java/lang/NegativeArraySizeException");

        String cpp = compileToCpp(method, 0);
        int firstGuard = cpp.indexOf("arg0 < 0");
        int firstThrow = cpp.indexOf("utils::throw_re", firstGuard);
        int secondGuard = cpp.indexOf("arg1 < 0");
        int secondThrow = cpp.indexOf("utils::throw_re", secondGuard);
        int allocation = cpp.indexOf("utils::create_multidim_array_value<5>");
        assertTrue(firstGuard >= 0 && firstThrow > firstGuard
                && secondGuard > firstThrow && secondThrow > secondGuard
                && allocation > secondThrow);
        assertTrue(cpp.contains("goto IR_CATCH_0;"));
    }

    @Test
    public void rejectsUnsupportedAfterPhaseEighteenArraysBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseEighteenArraysMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void lowersTableAndLookupSwitchesWithDefaultPhiTransfers() {
        IrMethod table = frontend.build("example/Math", tableSwitchMethod());
        IrBlock tableEntry = table.getBlocks().stream()
                .filter(block -> block.getTerminator() instanceof IrNodes.Switch)
                .findFirst().orElseThrow(AssertionError::new);
        IrNodes.Switch tableTerminator = (IrNodes.Switch) tableEntry.getTerminator();
        assertEquals(3, tableTerminator.getKeys().size());
        assertEquals(4, tableTerminator.getSuccessors().size());
        for (IrBlock successor : tableTerminator.getSuccessors()) {
            IrPhi stackPhi = successor.getPhis().stream()
                    .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK)
                    .findFirst().orElseThrow(AssertionError::new);
            assertTrue(stackPhi.getIncoming().containsKey(tableEntry));
        }
        String tableCpp = emitter.emitBody(table);
        assertTrue(tableCpp.contains("switch (arg0) {"));
        assertTrue(tableCpp.contains("case -1: {"));
        assertTrue(tableCpp.contains("case 1: {"));
        assertTrue(tableCpp.contains("default: {"));

        IrMethod lookup = frontend.build("example/Math", lookupSwitchMethod());
        IrBlock lookupEntry = lookup.getBlocks().stream()
                .filter(block -> block.getTerminator() instanceof IrNodes.Switch)
                .findFirst().orElseThrow(AssertionError::new);
        IrNodes.Switch lookupTerminator = (IrNodes.Switch) lookupEntry.getTerminator();
        assertEquals(Arrays.asList(-7, 42), lookupTerminator.getKeys());
        assertEquals(3, lookupTerminator.getSuccessors().size());
        IrPhi defaultStackPhi = lookupTerminator.getDefaultTarget().getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK)
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(defaultStackPhi.getIncoming().containsKey(lookupEntry));
        String lookupCpp = emitter.emitBody(lookup);
        assertTrue(lookupCpp.contains("case -7: {"));
        assertTrue(lookupCpp.contains("case 42: {"));
        assertTrue(lookupCpp.contains("default: {"));
    }

    @Test
    public void switchDefaultParticipatesInCarrierValidation() {
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> frontend.build("example/Math", switchDefaultCarrierMismatchMethod()));

        assertTrue(error.getMessage().contains("Mismatched operand-stack types at CFG merge"));
    }

    @Test
    public void lowersStringAndObjectAnewarrayWithFailureRouting() {
        MethodNode stringArray = newObjectArrayCatchMethod();
        IrMethod ir = frontend.build("example/Math", stringArray);
        assertTrue(ir.toString().contains("anewarray java/lang/String"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));
        MethodContext stringContext = new MethodContext(obfuscator, stringArray, 0, owner(), 0);
        compiler.processMethod(stringContext);
        String stringCpp = stringContext.output.toString();
        int allocation = stringCpp.indexOf("env->NewObjectArray(arg0");
        int nullCheck = stringCpp.indexOf("== nullptr", allocation);
        int exceptionCheck = stringCpp.indexOf("env->ExceptionCheck()", allocation);
        int dispatch = stringCpp.indexOf("goto IR_CATCH_0;", allocation);
        assertTrue(allocation >= 0 && nullCheck > allocation && exceptionCheck > allocation
                && dispatch > exceptionCheck);
        assertTrue(stringCpp.contains("utils::throw_re"));

        MethodNode objectArray = newObjectArrayMethod();
        MethodContext objectContext = new MethodContext(obfuscator, objectArray, 1, owner(), 0);
        compiler.processMethod(objectContext);
        String objectCpp = objectContext.output.toString();
        assertTrue(objectCpp.contains("env->NewObjectArray(arg0"));
        assertTrue(objectCpp.contains("env->GetArrayLength"));
        assertTrue(obfuscator.getCachedClasses().getCache().containsKey("java/lang/String"));
        assertTrue(obfuscator.getCachedClasses().getCache().containsKey("java/lang/Object"));
    }

    @Test
    public void resolvesArrayComponentAnewarrayWithFindClass() {
        MethodNode method = newNestedObjectArrayMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains(
                "// IR codegen: example/Math.allocateStringRows(I)I"));
        assertTrue(cpp.contains("env->FindClass(((char *)(string_pool + "));
        assertTrue(cpp.contains("env->NewObjectArray(arg0"));
        assertFalse(cpp.contains("utils::find_class_wo_static(env, classloader"));
        assertTrue(obfuscator.getCachedClasses().getCache()
                .containsKey("[Ljava/lang/String;"));
        assertFalse(obfuscator.getCachedStrings().getCache()
                .containsKey("[Ljava.lang.String;"));
    }

    @Test
    public void rejectsUnsupportedInstructionAfterAnewarrayBeforeMutation() {
        MethodNode method = unsupportedAfterObjectArrayMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.POP2, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void lowersStringLengthAsDedicatedIntrinsic() {
        IrMethod ir = frontend.build("example/Math", stringLengthMethod());
        assertTrue(ir.toString().contains("stringlength"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, stringLengthMethod(), 0,
                owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->GetStringLength"));
        assertFalse(cpp.contains("CallIntMethod"));
        assertFalse(cpp.contains("GetMethodID"));
    }

    @Test
    public void catchesArrayBoundsExceptionOnIrPath() {
        MethodNode method = arrayBoundsCatchMethod("catchBounds",
                "java/lang/ArrayIndexOutOfBoundsException");
        IrMethod ir = frontend.build("example/Math", method);

        IrBlock protectedBlock = ir.getBlocks().stream()
                .filter(block -> !block.getExceptionEdges().isEmpty())
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("java/lang/ArrayIndexOutOfBoundsException",
                protectedBlock.getExceptionEdges().get(0).getCatchType());
        IrBlock handler = protectedBlock.getExceptionEdges().get(0).getHandler();
        assertTrue(handler.getInstructions().get(0) instanceof IrNodes.CaughtException);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);
        assertTrue(cpp.contains("// IR codegen: example/Math.catchBounds([I)I"));
        assertFalse(cpp.contains("cstack"));
        assertTrue(cpp.contains("env->GetIntArrayRegion"));
        assertTrue(cpp.contains("= -1;"));
        assertTrue(cpp.contains("goto IR_CATCH_0;"));
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->ExceptionClear();"));
        assertTrue(cpp.contains("env->IsInstanceOf((jobject) caught_exception"));
        assertTrue(cpp.contains("env->Throw(caught_exception);"));
        assertTrue(cpp.contains("= -7;"));
        assertEquals(cpp.indexOf("IR_CATCH_0:"), cpp.lastIndexOf("IR_CATCH_0:"));
        int arrayCall = cpp.indexOf("env->GetIntArrayRegion");
        int dispatchGoto = cpp.indexOf("goto IR_CATCH_0;", arrayCall);
        int successfulLoad = cpp.indexOf(" = iaload", arrayCall);
        int swallowedReturn = cpp.indexOf("return 0;", arrayCall);
        assertTrue(arrayCall >= 0 && dispatchGoto > arrayCall
                && successfulLoad > dispatchGoto);
        assertTrue(swallowedReturn < 0 || swallowedReturn > successfulLoad);
    }

    @Test
    public void unmatchedCatchRethrowsPendingException() {
        MethodNode method = arrayBoundsCatchMethod("rethrowBounds",
                "java/lang/NullPointerException");
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);
        assertTrue(cpp.contains("// IR codegen: example/Math.rethrowBounds([I)I"));
        int typeTest = cpp.indexOf("env->IsInstanceOf((jobject) caught_exception");
        int rethrow = cpp.indexOf("env->Throw(caught_exception);");
        assertTrue(typeTest >= 0 && rethrow > typeTest);
    }

    @Test
    public void catchesExplicitAthrowOnIrPath() {
        MethodNode method = explicitThrowCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.getBlocks().stream()
                .anyMatch(block -> block.getTerminator() instanceof IrNodes.Throw));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue((method.access & Opcodes.ACC_NATIVE) != 0);
        assertTrue(cpp.contains("// IR codegen: example/Math.catchThrown"));
        assertTrue(cpp.contains("env->Throw((jthrowable) arg0);"));
        assertTrue(cpp.contains("goto IR_CATCH_0;"));
    }

    @Test
    public void representsCatchAllWithoutATypeTest() {
        MethodNode method = arrayBoundsCatchMethod("catchAny", null);
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.getBlocks().stream()
                .flatMap(block -> block.getExceptionEdges().stream())
                .anyMatch(edge -> edge.getCatchType() == null));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        String cpp = context.output.toString();
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertFalse(cpp.contains("env->IsInstanceOf((jobject) caught_exception"));
    }

    @Test
    public void emptyExceptionTableStillBuildsNormally() {
        MethodNode method = addMethod();
        assertTrue(method.tryCatchBlocks.isEmpty());
        IrMethod ir = frontend.build("example/Math", method);

        assertTrue(ir.getBlocks().stream()
                .allMatch(block -> block.getExceptionEdges().isEmpty()));
        assertTrue(emitter.emitBody(ir).contains("return v2;"));
    }

    @Test
    public void lowersCheckcastAndInstanceofWithJvmNullSemantics() {
        MethodNode method = checkCastInstanceOfMethod("typeTest", "java/lang/String");
        IrMethod ir = frontend.build("example/Math", method);
        assertTrue(ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .anyMatch(instruction -> instruction instanceof IrNodes.CheckCast));
        assertTrue(ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .anyMatch(instruction -> instruction instanceof IrNodes.InstanceOf));
        assertTrue(ir.toString().contains("checkcast java/lang/String"));
        assertTrue(ir.toString().contains("instanceof java/lang/String"));

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("arg0 != nullptr"));
        assertTrue(cpp.contains("env->IsInstanceOf(arg0"));
        assertTrue(cpp.contains("== 0"));
        assertTrue(cpp.contains(" = arg0;"));
        assertTrue(cpp.contains(" = 0;"));
        assertTrue(cpp.contains("utils::throw_re"));
        assertTrue(obfuscator.getCachedClasses().getCache()
                .containsKey("java/lang/String"));
    }

    @Test
    public void resolvesArrayCheckcastAndInstanceofWithFindClass() {
        MethodNode method = checkCastInstanceOfMethod("arrayTypeTest",
                "[Ljava/lang/String;");
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        assertTrue(cpp.contains("// IR codegen: example/Math.arrayTypeTest"));
        assertTrue(cpp.contains("env->FindClass(((char *)(string_pool + "));
        assertTrue(cpp.contains("env->IsInstanceOf(arg0"));
        assertTrue(obfuscator.getCachedClasses().getCache()
                .containsKey("[Ljava/lang/String;"));
        assertFalse(obfuscator.getCachedStrings().getCache()
                .containsKey("[Ljava.lang.String;"));
    }

    @Test
    public void checkcastFailureInsideTryUsesSharedCatchDispatch() {
        MethodNode method = checkCastCatchMethod();
        IrMethod ir = frontend.build("example/Math", method);
        IrBlock checkCastBlock = ir.getBlocks().stream()
                .filter(block -> block.getInstructions().stream()
                        .anyMatch(instruction -> instruction instanceof IrNodes.CheckCast))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals("java/lang/ClassCastException",
                checkCastBlock.getExceptionEdges().get(0).getCatchType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);

        String cpp = context.output.toString();
        int failedCast = cpp.indexOf("env->IsInstanceOf(arg0");
        int throwCast = cpp.indexOf("utils::throw_re", failedCast);
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", throwCast);
        assertTrue(failedCast >= 0 && throwCast > failedCast && dispatch > throwCast);
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(cpp.contains("env->ExceptionClear();"));
        assertTrue(cpp.contains("= -15;"));
    }

    @Test
    public void lowersTwoSlotLongLocalsArithmeticAndConversions() {
        IrMethod arithmetic = frontend.build("example/Math", longArithmeticMethod());
        String pretty = arithmetic.toString();
        assertTrue(pretty.contains("%arg0:i64, %arg1:i64"));
        assertTrue(pretty.contains("ladd"));
        assertTrue(pretty.contains("lsub"));
        assertTrue(pretty.contains("lmul"));

        String cpp = emitter.emitBody(arithmetic);
        assertTrue(cpp.contains("jlong v2;"));
        assertTrue(cpp.contains("(uint64_t)"));
        assertTrue(cpp.contains("1LL"));
        assertTrue(cpp.contains("return"));
        assertFalse(cpp.contains("julong"));

        IrMethod conversion = frontend.build("example/Math", longConversionMethod());
        assertTrue(conversion.toString().contains("i2l"));
        assertTrue(conversion.toString().contains("l2i"));
        String conversionCpp = emitter.emitBody(conversion);
        assertTrue(conversionCpp.contains("(jlong) arg0"));
        assertTrue(conversionCpp.contains("(jint) (uint32_t)"));
    }

    @Test
    public void lowersLongBitwiseAndShiftFamilyWithTypedCountsAndMasking() {
        IrMethod ir = frontend.build("example/Math", longBitwiseShiftMethod());
        String pretty = ir.toString();
        assertTrue(pretty.contains("land"));
        assertTrue(pretty.contains("lor"));
        assertTrue(pretty.contains("lxor"));
        assertTrue(pretty.contains("lshl"));
        assertTrue(pretty.contains("lshr"));
        assertTrue(pretty.contains("lushr"));

        List<IrNodes.LongShift> shifts = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.LongShift.class::isInstance)
                .map(IrNodes.LongShift.class::cast)
                .collect(Collectors.toList());
        assertEquals(3, shifts.size());
        for (IrNodes.LongShift shift : shifts) {
            assertEquals(IrType.I64, shift.getValue().getType());
            assertEquals(IrType.I32, shift.getCount().getType());
            assertEquals(IrType.I64, shift.getResult().getType());
        }

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("(uint64_t) arg0 & (uint64_t) arg1"));
        assertTrue(cpp.contains("(uint64_t) v3 | (uint64_t) arg1"));
        assertTrue(cpp.contains("(uint64_t) v4 ^ (uint64_t) arg1"));
        assertTrue(cpp.contains("(uint64_t) v5 << ((uint32_t) arg2 & 63)"));
        assertTrue(cpp.contains("(int64_t) v6 >> ((uint32_t) arg2 & 63)"));
        assertTrue(cpp.contains("(uint64_t) v7 >> ((uint32_t) arg2 & 63)"));
    }

    @Test
    public void emitsWrappingLongLeftShiftThroughUnsignedCarrier() {
        String cpp = emitter.emitBody(frontend.build(
                "example/Math", wrappingLongShiftMethod()));

        assertTrue(cpp.contains("9223372036854775807LL"));
        assertTrue(cpp.contains("(jlong) ((uint64_t)"));
        assertTrue(cpp.contains("<< ((uint32_t)"));
        assertTrue(cpp.contains("& 63)"));
        assertEquals(-2L, Long.MAX_VALUE << 65);
    }

    @Test
    public void numbersWideStackPhiSlotsByJvmSlotWidth() {
        IrMethod ir = frontend.build("example/Math", wideStackPhiMethod());
        IrBlock join = ir.getBlocks().stream()
                .filter(block -> block.getPhis().stream()
                        .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK)
                        .count() == 2)
                .findFirst().orElseThrow(AssertionError::new);
        IrPhi longPhi = join.getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK
                        && phi.getResult().getType() == IrType.I64)
                .findFirst().orElseThrow(AssertionError::new);
        IrPhi intPhi = join.getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK
                        && phi.getResult().getType() == IrType.I32)
                .findFirst().orElseThrow(AssertionError::new);

        assertEquals(0, longPhi.getSlotIndex());
        assertEquals(2, intPhi.getSlotIndex());
        assertEquals(2, longPhi.getIncoming().size());
        assertEquals(2, intPhi.getIncoming().size());
        assertTrue(emitter.emitBody(ir).contains("jlong"));
    }

    @Test
    public void rejectsUnsupportedWideOperationBeforeMutation() {
        MethodNode method = unsupportedWideOperationMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.JSR, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void lowersExplicitMonitorEnterExitAndNullCatchEdge() {
        MethodNode explicit = explicitMonitorMethod();
        IrMethod explicitIr = frontend.build("example/Math", explicit);
        List<IrInstruction> monitorInstructions = explicitIr.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(instruction -> instruction instanceof IrNodes.MonitorEnter
                        || instruction instanceof IrNodes.MonitorExit)
                .collect(Collectors.toList());
        assertEquals(2, monitorInstructions.size());
        assertTrue(monitorInstructions.get(0) instanceof IrNodes.MonitorEnter);
        assertTrue(monitorInstructions.get(1) instanceof IrNodes.MonitorExit);
        assertTrue(explicitIr.toString().contains("monitorenter %arg0"));
        assertTrue(explicitIr.toString().contains("monitorexit %v"));

        MethodNode caught = nullMonitorCatchMethod();
        IrMethod caughtIr = frontend.build("example/Math", caught);
        IrBlock enterBlock = caughtIr.getBlocks().stream()
                .filter(block -> block.getInstructions().stream()
                        .anyMatch(IrNodes.MonitorEnter.class::isInstance))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, enterBlock.getExceptionEdges().size());
        assertTrue(enterBlock.getExceptionEdges().get(0).getCatchType() == null);

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, caught, 0, owner(), 0);
        String cpp = emitter.emitBody(caughtIr, context);
        assertTrue(cpp.contains("utils::throw_re"));
        assertTrue(cpp.contains("env->MonitorEnter(v"));
        assertTrue(cpp.contains("env->MonitorExit(v"));
        assertTrue(cpp.contains("goto IR_CATCH_0;"));
        assertTrue(cpp.contains("caught_exception = env->ExceptionOccurred();"));
    }

    @Test
    public void lowersSynchronizedMethodsAndClearsJvmSynchronizationFlag() {
        MethodNode staticMethod = synchronizedStaticCounterMethod();
        IrMethod staticIr = frontend.build("example/Math", staticMethod);
        assertTrue(staticIr.isStaticMethod());
        assertTrue(staticIr.isSynchronizedMethod());
        assertTrue(staticIr.toString().contains("[static synchronized]"));

        NativeObfuscator staticObfuscator = new NativeObfuscator();
        MethodContext staticContext = new MethodContext(
                staticObfuscator, staticMethod, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(staticObfuscator))
                .processMethod(staticContext);
        assertEquals(0, staticMethod.access & Opcodes.ACC_SYNCHRONIZED);
        assertTrue((staticMethod.access & Opcodes.ACC_NATIVE) != 0);
        String staticCpp = staticContext.output.toString();
        assertTrue(staticCpp.contains("env->MonitorEnter(clazz)"));
        assertTrue(staticCpp.contains("env->MonitorExit(clazz)"));

        MethodNode instanceMethod = synchronizedInstanceMethod();
        IrMethod instanceIr = frontend.build("example/Math", instanceMethod);
        assertFalse(instanceIr.isStaticMethod());
        assertTrue(instanceIr.isSynchronizedMethod());
        NativeObfuscator instanceObfuscator = new NativeObfuscator();
        MethodContext instanceContext = new MethodContext(
                instanceObfuscator, instanceMethod, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(instanceObfuscator))
                .processMethod(instanceContext);
        assertEquals(0, instanceMethod.access & Opcodes.ACC_SYNCHRONIZED);
        String instanceCpp = instanceContext.output.toString();
        assertTrue(instanceCpp.contains("env->MonitorEnter(obj)"));
        assertTrue(instanceCpp.contains("env->MonitorExit(obj)"));
        assertTrue(instanceCpp.contains(
                "jthrowable synchronized_exception = env->ExceptionOccurred();"));
        assertTrue(instanceCpp.contains("env->ExceptionClear();"));
        assertTrue(instanceCpp.contains("env->Throw(synchronized_exception);"));
    }

    @Test
    public void rejectsUnstructuredMonitorPairingBeforeMutation() {
        MethodNode method = unstructuredMonitorMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.MONITORENTER, error.getOpcode());
        assertTrue(error.getMessage().contains("monitor is held"));
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    @Test
    public void executesMonitorAndSynchronizedSemanticsWhenToolchainAvailable()
            throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR monitor runtime test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR monitor runtime test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR monitor runtime test");

        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode owner = owner();
        MethodNode staticMethod = synchronizedStaticCounterMethod();
        MethodNode instanceMethod = synchronizedInstanceMethod();
        MethodNode explicitMethod = explicitMonitorMethod();
        MethodNode nullMethod = nullMonitorCatchMethod();
        String staticBody = emitter.emitBody(
                frontend.build(owner.name, staticMethod),
                new MethodContext(obfuscator, staticMethod, 0, owner, 0));
        String instanceBody = emitter.emitBody(
                frontend.build(owner.name, instanceMethod),
                new MethodContext(obfuscator, instanceMethod, 1, owner, 0));
        String explicitBody = emitter.emitBody(
                frontend.build(owner.name, explicitMethod),
                new MethodContext(obfuscator, explicitMethod, 2, owner, 0));
        String nullBody = emitter.emitBody(
                frontend.build(owner.name, nullMethod),
                new MethodContext(obfuscator, nullMethod, 3, owner, 0));

        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "static int enter_count = 0;\n"
                + "static int exit_count = 0;\n"
                + "static int monitor_depth = 0;\n"
                + "static jobject expected_monitor = nullptr;\n"
                + "static jobject active_monitor = nullptr;\n"
                + "static jthrowable pending_exception = nullptr;\n"
                + "static char string_pool_storage[512] = {};\n"
                + "static char *string_pool = string_pool_storage;\n"
                + "static jthrowable fake_exception() {\n"
                + "    return reinterpret_cast<jthrowable>(0x7000);\n"
                + "}\n"
                + "static jint JNICALL fake_monitor_enter(JNIEnv *, jobject monitor) {\n"
                + "    if (monitor == nullptr || monitor != expected_monitor) {\n"
                + "        pending_exception = fake_exception();\n"
                + "        return -1;\n"
                + "    }\n"
                + "    active_monitor = monitor;\n"
                + "    ++monitor_depth;\n"
                + "    ++enter_count;\n"
                + "    return 0;\n"
                + "}\n"
                + "static jint JNICALL fake_monitor_exit(JNIEnv *, jobject monitor) {\n"
                + "    if (monitor_depth == 0 || monitor != active_monitor) {\n"
                + "        pending_exception = fake_exception();\n"
                + "        return -1;\n"
                + "    }\n"
                + "    --monitor_depth;\n"
                + "    ++exit_count;\n"
                + "    if (monitor_depth == 0) active_monitor = nullptr;\n"
                + "    return 0;\n"
                + "}\n"
                + "static jboolean JNICALL fake_exception_check(JNIEnv *) {\n"
                + "    return pending_exception == nullptr ? JNI_FALSE : JNI_TRUE;\n"
                + "}\n"
                + "static jthrowable JNICALL fake_exception_occurred(JNIEnv *) {\n"
                + "    return pending_exception;\n"
                + "}\n"
                + "static void JNICALL fake_exception_clear(JNIEnv *) {\n"
                + "    pending_exception = nullptr;\n"
                + "}\n"
                + "static jint JNICALL fake_throw(JNIEnv *, jthrowable exception) {\n"
                + "    pending_exception = exception;\n"
                + "    return 0;\n"
                + "}\n"
                + "namespace utils {\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int) {\n"
                + "    pending_exception = fake_exception();\n"
                + "}\n"
                + "}\n"
                + "static jint synchronized_static(JNIEnv *env, jclass clazz, jint arg0) {\n"
                + staticBody
                + "}\n"
                + "static jint synchronized_instance(JNIEnv *env, jobject obj, jint arg0) {\n"
                + instanceBody
                + "}\n"
                + "static void explicit_monitor(JNIEnv *env, jobject arg0) {\n"
                + explicitBody
                + "}\n"
                + "static jint null_monitor(JNIEnv *env) {\n"
                + nullBody
                + "}\n"
                + "int main() {\n"
                + "    JNINativeInterface_ functions = {};\n"
                + "    functions.MonitorEnter = fake_monitor_enter;\n"
                + "    functions.MonitorExit = fake_monitor_exit;\n"
                + "    functions.ExceptionCheck = fake_exception_check;\n"
                + "    functions.ExceptionOccurred = fake_exception_occurred;\n"
                + "    functions.ExceptionClear = fake_exception_clear;\n"
                + "    functions.Throw = fake_throw;\n"
                + "    JNIEnv_ env_value = {};\n"
                + "    env_value.functions = &functions;\n"
                + "    JNIEnv *env = &env_value;\n"
                + "    jclass clazz = reinterpret_cast<jclass>(0x1000);\n"
                + "    jobject object = reinterpret_cast<jobject>(0x2000);\n"
                + "    expected_monitor = reinterpret_cast<jobject>(clazz);\n"
                + "    jint counter = 0;\n"
                + "    for (int i = 0; i < 5; ++i) {\n"
                + "        counter = synchronized_static(env, clazz, counter);\n"
                + "    }\n"
                + "    if (counter != 5 || enter_count != 5 || exit_count != 5) return 1;\n"
                + "    expected_monitor = object;\n"
                + "    if (synchronized_instance(env, object, 0) != 5) return 2;\n"
                + "    if (monitor_depth != 0 || enter_count != 6 || exit_count != 6) return 3;\n"
                + "    if (synchronized_instance(env, object, 1) != 0) return 4;\n"
                + "    if (pending_exception == nullptr || monitor_depth != 0"
                + " || enter_count != 7 || exit_count != 7) return 5;\n"
                + "    fake_exception_clear(env);\n"
                + "    explicit_monitor(env, object);\n"
                + "    if (monitor_depth != 0 || enter_count != 8 || exit_count != 8) return 6;\n"
                + "    int enters_before_null = enter_count;\n"
                + "    if (null_monitor(env) != 1) return 7;\n"
                + "    if (pending_exception != nullptr || enter_count != enters_before_null)"
                + " return 8;\n"
                + "    return monitor_depth == 0 ? 0 : 9;\n"
                + "}\n";

        Path directory = Files.createTempDirectory("ir-monitor-run");
        Path sourceFile = directory.resolve("monitors.cpp");
        Path binary = directory.resolve("monitors");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process compileProcess = new ProcessBuilder(gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                sourceFile.toString(), "-o", binary.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int compileExit = compileProcess.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput),
                StandardCharsets.UTF_8);
        assertEquals(0, compileExit, "g++ failed:\n" + output + "\nSource:\n" + source);

        Process runProcess = new ProcessBuilder(binary.toString()).start();
        assertEquals(0, runProcess.waitFor(),
                "Generated monitor lowering violated lock or exception semantics");
    }

    @Test
    public void lowersLongCompareAsSignedTernaryWithoutNanGuardOrSubtract() {
        IrMethod ir = frontend.build("example/Math", longCompareMethod());
        String pretty = ir.toString();
        assertTrue(pretty.contains(" = lcmp %arg0, %arg1"));

        IrNodes.LongCompare compare = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.LongCompare.class::isInstance)
                .map(IrNodes.LongCompare.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(IrType.I64, compare.getLeft().getType());
        assertEquals(IrType.I64, compare.getRight().getType());
        assertEquals(IrType.I32, compare.getResult().getType());

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("((int64_t) arg0 > (int64_t) arg1) ? 1 :"));
        assertTrue(cpp.contains("((int64_t) arg0 < (int64_t) arg1) ? -1 : 0"));
        assertFalse(cpp.contains("std::isnan"));
        assertFalse(cpp.contains("arg0 - "));
        assertFalse(cpp.contains("- (int64_t) arg1"));
        assertFalse(cpp.contains("- (uint64_t) arg1"));
    }

    @Test
    public void longCompareResultDrivesIntZeroBranch() {
        IrMethod ir = frontend.build("example/Math", longCompareBranchMethod());
        String pretty = ir.toString();
        assertTrue(pretty.contains("lcmp"));

        String cpp = emitter.emitBody(ir);
        assertTrue(cpp.contains("((int64_t) arg0 > (int64_t) arg1) ? 1 :"));
        assertTrue(cpp.contains(" >= 0) {"));
    }

    @Test
    public void executesLongCompareSemanticsWhenToolchainAvailable() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR LCMP runtime test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR LCMP runtime test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR LCMP runtime test");

        String body = emitter.emitBody(frontend.build("example/Math", longCompareMethod()));
        String min = "(-9223372036854775807LL - 1LL)";
        String max = "9223372036854775807LL";
        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "static jint lcmp(jlong arg0, jlong arg1) {\n"
                + body
                + "}\n"
                + "int main() {\n"
                + "    if (lcmp(1LL, 2LL) != -1) return 1;\n"
                + "    if (lcmp(2LL, 1LL) != 1) return 2;\n"
                + "    if (lcmp(5LL, 5LL) != 0) return 3;\n"
                + "    if (lcmp(" + min + ", -1LL) != -1) return 4;\n"
                + "    if (lcmp(-1LL, " + min + ") != 1) return 5;\n"
                + "    if (lcmp(" + min + ", " + min + ") != 0) return 6;\n"
                // A subtract-based lowering overflows on MIN vs MAX and
                // misorders both directions below.
                + "    if (lcmp(" + min + ", " + max + ") != -1) return 7;\n"
                + "    if (lcmp(" + max + ", " + min + ") != 1) return 8;\n"
                + "    return 0;\n"
                + "}\n";

        Path directory = Files.createTempDirectory("ir-lcmp-run");
        Path sourceFile = directory.resolve("lcmp.cpp");
        Path binary = directory.resolve("lcmp");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process compileProcess = new ProcessBuilder(gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                sourceFile.toString(), "-o", binary.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int compileExit = compileProcess.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput),
                StandardCharsets.UTF_8);
        assertEquals(0, compileExit, "g++ failed:\n" + output + "\nSource:\n" + source);

        Process runProcess = new ProcessBuilder(binary.toString()).start();
        assertEquals(0, runProcess.waitFor(),
                "Generated LCMP lowering returned a wrong three-way result");
    }

    @Test
    public void splitsReferenceAndIntReuseInTemporaryLocal() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "reuseTemporary", "()I", null, null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 21));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;

        IrMethod ir = frontend.build("example/Math", method);

        assertTrue(ir.toString().contains("return"));
        assertEquals(1, method.maxLocals);
        assertEquals(0, ((VarInsnNode) method.instructions.get(1)).var);
        assertEquals(0, ((VarInsnNode) method.instructions.get(5)).var);
    }

    @Test
    public void rejectsIntStoreIntoInstanceReceiverLocal() {
        MethodNode method = new MethodNode(Opcodes.ASM9, 0,
                "badStore", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 0));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 1;

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));
        assertTrue(error.getMessage().contains("Local 0 is ref"));
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    @Test
    public void generatedCppPassesGppSyntaxCheckWhenToolchainAvailable() throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR smoke test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR smoke test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR smoke test");

        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode owner = owner();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));
        MethodNode[] methods = {
                addMethod(), sumToMethod(), subMulMethod(), incrementFieldMethod(),
                staticInvokeMethod(), virtualInvokeMethod(), bitwiseShiftMethod(),
                unaryMethod(), intArrayMethod(), stringLengthMethod(),
                stringLdcMethod(), classLdcMethod(), primitiveClassLdcMethod(),
                longLdcMethod(),
                arrayBoundsCatchMethod("catchBounds", "java/lang/ArrayIndexOutOfBoundsException"),
                arrayBoundsCatchMethod("rethrowBounds", "java/lang/NullPointerException"),
                explicitThrowCatchMethod(), arrayBoundsCatchMethod("catchAny", null),
                divRemMethod(), divideCatchMethod(), newIntArrayCatchMethod(),
                staticIntFieldMethod(), tableSwitchMethod(), lookupSwitchMethod(),
                newObjectArrayCatchMethod(), newObjectArrayMethod(),
                newNestedObjectArrayMethod(),
                checkCastInstanceOfMethod("typeTest", "java/lang/String"),
                checkCastInstanceOfMethod("arrayTypeTest", "[Ljava/lang/String;"),
                checkCastCatchMethod(), longArithmeticMethod(), longBitwiseShiftMethod(),
                longDivRemMethod(), longNegateMethod(), longDivideCatchMethod(),
                wrappingLongShiftMethod(), longConversionMethod(),
                longCompareMethod(), longCompareBranchMethod(),
                wideStackPhiMethod(), constructObjectMethod(), staticLongInvokeMethod(),
                virtualStringInvokeMethod(), staticStringInvokeMethod(),
                staticVoidLongInvokeMethod(),
                interfaceInvokeMethod("interfaceInt", "adjust", "(I)I"),
                interfaceInvokeMethod("interfaceLong", "adjustLong", "(J)J"),
                interfaceInvokeMethod(
                        "interfaceObject", "create", "(I)Ljava/lang/Object;"),
                interfaceInvokeMethod(
                        "interfaceVoid", "accept", "(JLjava/lang/Object;)V"),
                specialInvokeMethod(
                        "specialInt", "example/Base", "superInt", "(I)I"),
                specialInvokeMethod(
                        "specialLong", "example/Math", "privateLong", "(J)J"),
                specialInvokeMethod("specialObject", "example/Base", "superText",
                        "()Ljava/lang/String;"),
                specialInvokeMethod("specialVoid", "example/Base", "superAccept",
                        "(JLjava/lang/Object;)V"),
                nullableInterfaceInvokeMethod(), returnAllocatedObjectMethod(),
                returnNullMethod(), referenceNullBranchMethod("ifNull", Opcodes.IFNULL),
                referenceNullBranchMethod("ifNonNull", Opcodes.IFNONNULL),
                referenceCompareBranchMethod("acmpEq", Opcodes.IF_ACMPEQ),
                referenceCompareBranchMethod("acmpNe", Opcodes.IF_ACMPNE),
                popUnusedCategoryOneInvokeResultMethod(), returnAllocatedObjectArrayMethod(),
                instanceFieldRoundTripMethod(
                        "instanceIntField", "I", Opcodes.ILOAD, Opcodes.IRETURN),
                instanceFieldRoundTripMethod(
                        "instanceLongField", "J", Opcodes.LLOAD, Opcodes.LRETURN),
                instanceFieldRoundTripMethod("instanceObjectField",
                        "Ljava/lang/Object;", Opcodes.ALOAD, Opcodes.ARETURN),
                instanceFieldRoundTripMethod(
                        "instanceArrayField", "[I", Opcodes.ALOAD, Opcodes.ARETURN),
                staticFieldRoundTripMethod(
                        "staticIntField", "I", Opcodes.ILOAD, Opcodes.IRETURN),
                staticFieldRoundTripMethod(
                        "staticLongField", "J", Opcodes.LLOAD, Opcodes.LRETURN),
                staticFieldRoundTripMethod("staticObjectField",
                        "Ljava/lang/Object;", Opcodes.ALOAD, Opcodes.ARETURN),
                staticFieldRoundTripMethod(
                        "staticArrayField", "[I", Opcodes.ALOAD, Opcodes.ARETURN),
                primitiveFieldConstantRoundTripMethod(
                        "booleanFalse", "Z", 0, false),
                primitiveFieldConstantRoundTripMethod(
                        "booleanTrue", "Z", 1, false),
                primitiveFieldConstantRoundTripMethod(
                        "byteNegative", "B", -7, false),
                primitiveFieldConstantRoundTripMethod(
                        "charAboveAscii", "C", 200, false),
                primitiveFieldConstantRoundTripMethod(
                        "shortNegative", "S", -300, false),
                primitiveFieldConstantRoundTripMethod(
                        "staticBooleanFalse", "Z", 0, true),
                primitiveFieldConstantRoundTripMethod(
                        "staticBooleanTrue", "Z", 1, true),
                primitiveFieldConstantRoundTripMethod(
                        "staticByteNegative", "B", -7, true),
                primitiveFieldConstantRoundTripMethod(
                        "staticCharAboveAscii", "C", 200, true),
                primitiveFieldConstantRoundTripMethod(
                        "staticShortNegative", "S", -300, true),
                staticPrimitiveInvokeMethod("staticBoolean",
                        "identityBoolean", "(Z)Z"),
                staticPrimitiveInvokeMethod("staticByte",
                        "identityByte", "(B)B"),
                staticPrimitiveInvokeMethod("staticChar",
                        "identityChar", "(C)C"),
                staticPrimitiveInvokeMethod("staticShort",
                        "identityShort", "(S)S"),
                virtualPrimitiveInvokeMethod("virtualBoolean",
                        "identityBoolean", "(Z)Z"),
                virtualPrimitiveInvokeMethod("virtualByte",
                        "identityByte", "(B)B"),
                virtualPrimitiveInvokeMethod("virtualChar",
                        "identityChar", "(C)C"),
                virtualPrimitiveInvokeMethod("virtualShort",
                        "identityShort", "(S)S"),
                interfaceInvokeMethod("interfaceBoolean",
                        "identityBoolean", "(Z)Z"),
                interfaceInvokeMethod("interfaceByte",
                        "identityByte", "(B)B"),
                interfaceInvokeMethod("interfaceChar",
                        "identityChar", "(C)C"),
                interfaceInvokeMethod("interfaceShort",
                        "identityShort", "(S)S"),
                specialInvokeMethod("specialBoolean", "example/Base",
                        "identityBoolean", "(Z)Z"),
                specialInvokeMethod("specialByte", "example/Base",
                        "identityByte", "(B)B"),
                specialInvokeMethod("specialChar", "example/Base",
                        "identityChar", "(C)C"),
                specialInvokeMethod("specialShort", "example/Base",
                        "identityShort", "(S)S"),
                instanceFieldRoundTripMethod(
                        "instanceFloatField", "F", Opcodes.FLOAD, Opcodes.FRETURN),
                instanceFieldRoundTripMethod(
                        "instanceDoubleField", "D", Opcodes.DLOAD, Opcodes.DRETURN),
                staticFieldRoundTripMethod(
                        "staticFloatField", "F", Opcodes.FLOAD, Opcodes.FRETURN),
                staticFieldRoundTripMethod(
                        "staticDoubleField", "D", Opcodes.DLOAD, Opcodes.DRETURN),
                floatingFieldConstantRoundTripMethod(
                        "floatNegativeZero", "F", -0.0f, false),
                floatingFieldConstantRoundTripMethod(
                        "doubleNan", "D",
                        Double.longBitsToDouble(0x7ff8000000001234L), true),
                staticPrimitiveInvokeMethod(
                        "staticFloat", "identityFloat", "(F)F"),
                staticPrimitiveInvokeMethod(
                        "staticDouble", "identityDouble", "(D)D"),
                virtualPrimitiveInvokeMethod(
                        "virtualFloat", "identityFloat", "(F)F"),
                virtualPrimitiveInvokeMethod(
                        "virtualDouble", "identityDouble", "(D)D"),
                interfaceInvokeMethod(
                        "interfaceFloat", "identityFloat", "(F)F"),
                interfaceInvokeMethod(
                        "interfaceDouble", "identityDouble", "(D)D"),
                specialInvokeMethod(
                        "specialFloat", "example/Base", "identityFloat", "(F)F"),
                specialInvokeMethod(
                        "specialDouble", "example/Base", "identityDouble", "(D)D"),
                floatingScalarOpsMethod(false), floatingScalarOpsMethod(true),
                floatingConversionMethod(Opcodes.I2F),
                floatingConversionMethod(Opcodes.F2I),
                floatingConversionMethod(Opcodes.L2F),
                floatingConversionMethod(Opcodes.F2L),
                floatingConversionMethod(Opcodes.I2D),
                floatingConversionMethod(Opcodes.D2I),
                floatingConversionMethod(Opcodes.L2D),
                floatingConversionMethod(Opcodes.D2L),
                floatingConversionMethod(Opcodes.F2D),
                floatingConversionMethod(Opcodes.D2F),
                floatingStackPhiMethod(false), floatingStackPhiMethod(true),
                swapMethod(Type.INT_TYPE, Type.FLOAT_TYPE),
                swapMethod(Type.FLOAT_TYPE, Type.getType(Object.class)),
                swapMethod(Type.getType(Object.class), Type.INT_TYPE),
                stackShuffleMethod("smokeDup2Form1", new int[]{Opcodes.DUP2},
                        new Type[]{Type.INT_TYPE, Type.FLOAT_TYPE},
                        new int[]{0, 1, 0, 1}),
                stackShuffleMethod("smokeDup2Form2", new int[]{Opcodes.DUP2},
                        new Type[]{Type.LONG_TYPE}, new int[]{0, 0}),
                stackShuffleMethod("smokeDupX2Form1", new int[]{Opcodes.DUP_X2},
                        new Type[]{Type.getType(Object.class), Type.INT_TYPE, Type.FLOAT_TYPE},
                        new int[]{2, 0, 1, 2}),
                stackShuffleMethod("smokeDupX2Form2", new int[]{Opcodes.DUP_X2},
                        new Type[]{Type.DOUBLE_TYPE, Type.getType(Object.class)},
                        new int[]{1, 0, 1}),
                stackShuffleMethod("smokeSwapDup2X1Form1",
                        new int[]{Opcodes.SWAP, Opcodes.DUP2_X1},
                        new Type[]{Type.getType(Object.class), Type.INT_TYPE, Type.FLOAT_TYPE},
                        new int[]{2, 1, 0, 2, 1}),
                stackShuffleMethod("smokeDup2X1Form2", new int[]{Opcodes.DUP2_X1},
                        new Type[]{Type.INT_TYPE, Type.DOUBLE_TYPE}, new int[]{1, 0, 1}),
                stackShuffleMethod("smokeDup2X2Form1", new int[]{Opcodes.DUP2_X2},
                        new Type[]{Type.INT_TYPE, Type.FLOAT_TYPE,
                                Type.getType(Object.class), Type.INT_TYPE},
                        new int[]{2, 3, 0, 1, 2, 3}),
                stackShuffleMethod("smokeDup2X2Form2", new int[]{Opcodes.DUP2_X2},
                        new Type[]{Type.FLOAT_TYPE, Type.getType(Object.class),
                                Type.LONG_TYPE}, new int[]{2, 0, 1, 2}),
                stackShuffleMethod("smokeDup2X2Form3", new int[]{Opcodes.DUP2_X2},
                        new Type[]{Type.DOUBLE_TYPE, Type.INT_TYPE,
                                Type.getType(Object.class)}, new int[]{1, 2, 0, 1, 2}),
                stackShuffleMethod("smokeDup2X2Form4", new int[]{Opcodes.DUP2_X2},
                        new Type[]{Type.LONG_TYPE, Type.DOUBLE_TYPE},
                        new int[]{1, 0, 1}),
                stackShuffleMethod("smokePop2Form1", new int[]{Opcodes.POP2},
                        new Type[]{Type.getType(Object.class), Type.INT_TYPE,
                                Type.FLOAT_TYPE}, new int[]{0}),
                stackShuffleMethod("smokePop2Form2", new int[]{Opcodes.POP2},
                        new Type[]{Type.FLOAT_TYPE, Type.DOUBLE_TYPE}, new int[]{0}),
                referenceArrayRoundTripMethod(false),
                referenceArrayRoundTripMethod(true),
                nullReferenceArrayLoadCatchMethod(),
                referenceArrayBoundsCatchMethod(false),
                referenceArrayBoundsCatchMethod(true),
                wrongTypeReferenceArrayStoreCatchMethod(),
                primitiveArrayRoundTripMethod(
                        "smokeBooleanArray", Opcodes.T_BOOLEAN, Type.BOOLEAN_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeByteArray", Opcodes.T_BYTE, Type.BYTE_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeCharArray", Opcodes.T_CHAR, Type.CHAR_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeShortArray", Opcodes.T_SHORT, Type.SHORT_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeIntArray", Opcodes.T_INT, Type.INT_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeFloatArray", Opcodes.T_FLOAT, Type.FLOAT_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeLongArray", Opcodes.T_LONG, Type.LONG_TYPE),
                primitiveArrayRoundTripMethod(
                        "smokeDoubleArray", Opcodes.T_DOUBLE, Type.DOUBLE_TYPE),
                multiArrayMethod("smokeIntMatrix", "[[I", 2),
                multiArrayMethod("smokeStringMatrix", "[[Ljava/lang/String;", 2),
                multiArrayNegativeCatchMethod(),
                nullReceiverGetFieldMethod(), nullReceiverPutFieldMethod(),
                intFieldConstructor(), floatingFieldConstructor(),
                referenceFieldConstructor()
        };
        StringBuilder generatedFunctions = new StringBuilder();
        for (int i = 0; i < methods.length; i++) {
            MethodContext context = new MethodContext(obfuscator, methods[i], i, owner, 0);
            compiler.processMethod(context);
            generatedFunctions.append(context.output);
        }

        // A real invokedynamic call site, lowered by the production preprocessing
        // pass and then admitted on the IR path, must also compile cleanly here.
        MethodNode indyMethod = stringConcatIndyMethod();
        by.radioegor146.bytecode.PreprocessorRunner.preprocess(owner, indyMethod,
                Platform.STD_JAVA);
        MethodContext indyContext = new MethodContext(
                obfuscator, indyMethod, methods.length, owner, 0);
        compiler.processMethod(indyContext);
        generatedFunctions.append(indyContext.output);

        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "#include <cmath>\n"
                + "#include <cstring>\n"
                + "#include <initializer_list>\n"
                + "#include <mutex>\n"
                + "#include <unordered_set>\n"
                + "namespace native_jvm {\n"
                + "namespace utils {\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int);\n"
                + "jclass find_class_wo_static(JNIEnv *, jobject, jstring);\n"
                + "jclass get_class_from_object(JNIEnv *, jobject);\n"
                + "jobject get_classloader_from_class(JNIEnv *, jclass);\n"
                + "jobject get_lookup(JNIEnv *, jclass);\n"
                + "jobjectArray create_multidim_array(JNIEnv *, jobject, jint, jint, "
                + "const char *, int, std::initializer_list<jint>);\n"
                + "template <int sort> jarray create_multidim_array_value("
                + "JNIEnv *, jint, jint, const char *, int, "
                + "std::initializer_list<jint>);\n"
                + "}\n"
                + "namespace classes { namespace smoke {\n"
                + "char *string_pool;\n"
                + "jstring cstrings[" + Math.max(1, obfuscator.getCachedStrings().size())
                + "];\n"
                + "std::mutex cclasses_mtx["
                + Math.max(1, obfuscator.getCachedClasses().size()) + "];\n"
                + "jclass cclasses[" + Math.max(1, obfuscator.getCachedClasses().size())
                + "];\n"
                + "jmethodID cmethods[" + Math.max(1, obfuscator.getCachedMethods().size())
                + "];\n"
                + "jfieldID cfields[" + Math.max(1, obfuscator.getCachedFields().size())
                + "];\n"
                + generatedFunctions
                + "} }\n"
                + "}\n";
        assertTrue(source.contains("IR codegen: example/Math.add(II)I"));
        assertTrue(source.contains("IR codegen: example/Math.sumTo(I)I"));
        assertTrue(source.contains("env->GetIntField"));
        assertTrue(source.contains("env->CallStaticIntMethod"));
        assertTrue(source.contains("env->CallIntMethod"));
        assertTrue(source.contains("env->GetArrayLength"));
        assertTrue(source.contains("env->GetIntArrayRegion"));
        assertTrue(source.contains("env->SetIntArrayRegion"));
        assertTrue(source.contains("env->GetStringLength"));
        assertTrue(source.contains("IR codegen: example/Math.ldcStrings()I"));
        assertTrue(source.contains("= (jobject) cstrings["));
        assertTrue(source.contains(
                "IR codegen: example/Math.ldcClasses()Ljava/lang/Class;"));
        assertTrue(source.contains("= (jobject) cclasses["));
        assertTrue(source.contains("IR codegen: example/Math.ldcLongs()J"));
        assertTrue(source.contains("4294967296LL"));
        assertTrue(source.contains("-1LL"));
        assertTrue(source.contains("caught_exception = env->ExceptionOccurred();"));
        assertTrue(source.contains("env->ExceptionClear();"));
        assertTrue(source.contains("env->IsInstanceOf"));
        assertTrue(source.contains("env->Throw(caught_exception);"));
        assertTrue(source.contains("(int32_t) arg0 / (int32_t) arg1"));
        assertTrue(source.contains(" % (int32_t)"));
        assertTrue(source.contains("IR codegen: example/Math.catchDivide(II)I"));
        assertTrue(source.contains("env->NewIntArray(arg0)"));
        assertTrue(source.contains("IR codegen: example/Math.allocate(I)I"));
        assertTrue(source.contains("env->GetStaticFieldID"));
        assertTrue(source.contains("env->GetStaticIntField"));
        assertTrue(source.contains("env->SetStaticIntField"));
        assertTrue(source.contains("IR codegen: example/Math.tableSelect(II)I"));
        assertTrue(source.contains("IR codegen: example/Math.lookupSelect(II)I"));
        assertTrue(source.contains("switch (arg0) {"));
        assertTrue(source.contains("default: {"));
        assertTrue(source.contains("IR codegen: example/Math.allocateStrings(I)I"));
        assertTrue(source.contains("IR codegen: example/Math.allocateObjects(I)I"));
        assertTrue(source.contains("IR codegen: example/Math.allocateStringRows(I)I"));
        assertTrue(source.contains("env->NewObjectArray(arg0"));
        assertTrue(source.contains("env->FindClass(((char *)(string_pool + "));
        assertTrue(source.contains("IR codegen: example/Math.typeTest"));
        assertTrue(source.contains("IR codegen: example/Math.arrayTypeTest"));
        assertTrue(source.contains("IR codegen: example/Math.catchCast"));
        assertTrue(source.contains("IR codegen: example/Math.longArithmetic(JJ)J"));
        assertTrue(source.contains(
                "IR codegen: example/Math.longBitwiseShift(JJI)J"));
        assertTrue(source.contains("IR codegen: example/Math.longDivRem(JJ)J"));
        assertTrue(source.contains("IR codegen: example/Math.longNegate(J)J"));
        assertTrue(source.contains("IR codegen: example/Math.catchLongDivide(JJ)J"));
        assertTrue(source.contains("(int64_t) arg0 / (int64_t) arg1"));
        assertTrue(source.contains(" % (int64_t)"));
        assertTrue(source.contains("(jlong) (-(uint64_t)"));
        assertTrue(source.contains(
                "IR codegen: example/Math.wrappingLongShift()J"));
        assertTrue(source.contains("IR codegen: example/Math.longConversion(I)I"));
        assertTrue(source.contains("IR codegen: example/Math.longCompare(JJ)I"));
        assertTrue(source.contains(
                "IR codegen: example/Math.longCompareBranch(JJ)I"));
        assertTrue(source.contains("((int64_t) arg0 > (int64_t) arg1) ? 1 :"));
        assertTrue(source.contains("IR codegen: example/Math.widePhi(JI)J"));
        assertTrue(source.contains("IR codegen: example/Math.constructObject()I"));
        assertTrue(source.contains("env->AllocObject(cclasses["));
        assertTrue(source.contains("env->CallNonvirtualVoidMethod"));
        assertTrue(source.contains("env->CallStaticLongMethod"));
        assertTrue(source.contains("env->CallObjectMethod"));
        assertTrue(source.contains("env->CallStaticObjectMethod"));
        assertTrue(source.contains("env->CallStaticVoidMethod"));
        assertTrue(source.contains(
                "IR codegen: example/Math.interfaceInt"
                        + "(Lexample/Phase11Interface;I)I"));
        assertTrue(source.contains("env->CallLongMethod"));
        assertTrue(source.contains("env->CallVoidMethod"));
        assertTrue(source.contains("env->CallNonvirtualIntMethod"));
        assertTrue(source.contains("env->CallNonvirtualLongMethod"));
        assertTrue(source.contains("env->CallNonvirtualObjectMethod"));
        assertTrue(source.contains("IR codegen: example/Math.specialVoid"));
        assertTrue(source.contains("IR codegen: example/Math.nullableInterface"));
        assertTrue(source.contains(
                "IR codegen: example/Math.returnAllocatedObject()Ljava/lang/Object;"));
        assertTrue(source.contains("IR codegen: example/Math.returnNull()Ljava/lang/Object;"));
        assertTrue(source.contains("IR codegen: example/Math.ifNull"));
        assertTrue(source.contains("if (arg0 == nullptr) {"));
        assertTrue(source.contains("IR codegen: example/Math.ifNonNull"));
        assertTrue(source.contains("if (arg0 != nullptr) {"));
        assertTrue(source.contains("IR codegen: example/Math.discardInvokeResult()V"));
        assertTrue(source.contains(
                "IR codegen: example/Math.returnAllocatedObjectArray(I)[Ljava/lang/Object;"));
        assertTrue(source.contains("return (jarray) v"));
        assertTrue(source.contains("env->GetLongField"));
        assertTrue(source.contains("env->SetLongField"));
        assertTrue(source.contains("env->GetObjectField"));
        assertTrue(source.contains("env->SetObjectField"));
        assertTrue(source.contains("env->GetStaticLongField"));
        assertTrue(source.contains("env->SetStaticLongField"));
        assertTrue(source.contains("env->GetStaticObjectField"));
        assertTrue(source.contains("env->SetStaticObjectField"));
        assertTrue(source.contains("env->GetBooleanField"));
        assertTrue(source.contains("env->SetBooleanField"));
        assertTrue(source.contains("env->GetByteField"));
        assertTrue(source.contains("env->SetByteField"));
        assertTrue(source.contains("env->GetCharField"));
        assertTrue(source.contains("env->SetCharField"));
        assertTrue(source.contains("env->GetShortField"));
        assertTrue(source.contains("env->SetShortField"));
        assertTrue(source.contains("env->GetStaticBooleanField"));
        assertTrue(source.contains("env->SetStaticBooleanField"));
        assertTrue(source.contains("env->GetStaticByteField"));
        assertTrue(source.contains("env->SetStaticByteField"));
        assertTrue(source.contains("env->GetStaticCharField"));
        assertTrue(source.contains("env->SetStaticCharField"));
        assertTrue(source.contains("env->GetStaticShortField"));
        assertTrue(source.contains("env->SetStaticShortField"));
        assertTrue(source.contains("env->CallStaticBooleanMethod"));
        assertTrue(source.contains("env->CallStaticByteMethod"));
        assertTrue(source.contains("env->CallStaticCharMethod"));
        assertTrue(source.contains("env->CallStaticShortMethod"));
        assertTrue(source.contains("env->CallBooleanMethod"));
        assertTrue(source.contains("env->CallByteMethod"));
        assertTrue(source.contains("env->CallCharMethod"));
        assertTrue(source.contains("env->CallShortMethod"));
        assertTrue(source.contains("env->CallNonvirtualBooleanMethod"));
        assertTrue(source.contains("env->CallNonvirtualByteMethod"));
        assertTrue(source.contains("env->CallNonvirtualCharMethod"));
        assertTrue(source.contains("env->CallNonvirtualShortMethod"));
        assertTrue(source.contains("env->GetFloatField"));
        assertTrue(source.contains("env->SetFloatField"));
        assertTrue(source.contains("env->GetDoubleField"));
        assertTrue(source.contains("env->SetDoubleField"));
        assertTrue(source.contains("env->GetStaticFloatField"));
        assertTrue(source.contains("env->SetStaticFloatField"));
        assertTrue(source.contains("env->GetStaticDoubleField"));
        assertTrue(source.contains("env->SetStaticDoubleField"));
        assertTrue(source.contains("env->CallStaticFloatMethod"));
        assertTrue(source.contains("env->CallStaticDoubleMethod"));
        assertTrue(source.contains("env->CallFloatMethod"));
        assertTrue(source.contains("env->CallDoubleMethod"));
        assertTrue(source.contains("env->CallNonvirtualFloatMethod"));
        assertTrue(source.contains("env->CallNonvirtualDoubleMethod"));
        assertTrue(source.contains("std::fmod"));
        assertTrue(source.contains("std::isnan"));
        assertTrue(source.contains("IR codegen: example/Math.swap"));
        assertTrue(source.contains("IR codegen: example/Math.smokeDup2Form1"));
        assertTrue(source.contains("IR codegen: example/Math.smokeSwapDup2X1Form1"));
        assertTrue(source.contains("IR codegen: example/Math.smokeDup2X2Form4"));
        assertTrue(source.contains("IR codegen: example/Math.smokePop2Form2"));
        assertTrue(source.contains("IR codegen: example/Math.objectArrayRoundTrip"));
        assertTrue(source.contains("IR codegen: example/Math.stringArrayRoundTrip"));
        assertTrue(source.contains("env->GetObjectArrayElement"));
        assertTrue(source.contains("env->SetObjectArrayElement"));
        assertTrue(source.contains("IR codegen: example/Math.catchNullAaload"));
        assertTrue(source.contains("IR codegen: example/Math.catchNegativeAaload"));
        assertTrue(source.contains("IR codegen: example/Math.catchUpperAaload"));
        assertTrue(source.contains("IR codegen: example/Math.catchArrayStore"));
        for (String carrier : Arrays.asList(
                "Boolean", "Byte", "Char", "Short", "Int", "Float", "Long", "Double")) {
            assertTrue(source.contains("env->New" + carrier + "Array"));
            assertTrue(source.contains("env->Get" + carrier + "ArrayRegion"));
            assertTrue(source.contains("env->Set" + carrier + "ArrayRegion"));
        }
        assertTrue(source.contains("IR codegen: example/Math.smokeIntMatrix"));
        assertTrue(source.contains("utils::create_multidim_array_value<5>"));
        assertTrue(source.contains("IR codegen: example/Math.smokeStringMatrix"));
        assertTrue(source.contains("utils::create_multidim_array(env, classloader"));
        assertTrue(source.contains("IR codegen: example/Math.catchNegativeMultiArray"));
        assertTrue(source.contains("uint32_t bits = 0x80000000U"));
        assertTrue(source.contains("uint64_t bits = 0x7ff8000000001234ULL"));
        assertTrue(source.contains("jfloat"));
        assertTrue(source.contains("jdouble"));
        assertTrue(source.contains("(jboolean)"));
        assertTrue(source.contains("(jbyte)"));
        assertTrue(source.contains("(jchar)"));
        assertTrue(source.contains("(jshort)"));
        assertTrue(source.contains("env->IsInstanceOf(arg0"));
        assertTrue(source.contains("IR codegen: example/Math.<init>(I)V"));
        assertTrue(source.contains("jobject ignored_hidden, jobject obj"));
        assertTrue(source.contains("uint64_t"));
        assertTrue(source.contains("(jlong)"));
        assertFalse(source.contains("juint"));
        assertFalse(source.contains("julong"));
        assertTrue(source.contains(
                "IR codegen: example/Math.concat(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(source.contains("utils::get_lookup(env, clazz)"));
        assertFalse(source.contains("native/magic"));

        Path directory = Files.createTempDirectory("ir-compile-smoke");
        Path sourceFile = directory.resolve("ir-smoke.cpp");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process process = new ProcessBuilder(gpp.toString(), "-std=c++17", "-fsyntax-only",
                "-I" + jniInclude, "-I" + platformInclude, sourceFile.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int exitCode = process.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput), StandardCharsets.UTF_8);
        assertEquals(0, exitCode, "g++ failed:\n" + output + "\nSource:\n" + source);
    }

    @Test
    public void rejectsUnsupportedInstructionsBeforeEmission() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupported", "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;

        assertThrows(UnsupportedIrConstructException.class,
                () -> frontend.build("example/Math", method));
    }

    private ClassNode owner() {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = "example/Math";
        owner.access = Opcodes.ACC_PUBLIC;
        owner.superName = "java/lang/Object";
        return owner;
    }

    private ClassNode constructorOwner(String name, String superName) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.name = name;
        owner.superName = superName;
        owner.access = Opcodes.ACC_PUBLIC;
        return owner;
    }

    private java.util.List<Integer> realOpcodes(MethodNode method) {
        java.util.List<Integer> opcodes = new java.util.ArrayList<>();
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                opcodes.add(instruction.getOpcode());
            }
        }
        return opcodes;
    }

    private java.util.List<Integer> variableIndexes(
            MethodNode method, int opcode) {
        java.util.List<Integer> indexes = new java.util.ArrayList<>();
        for (org.objectweb.asm.tree.AbstractInsnNode instruction
                : method.instructions) {
            if (instruction.getOpcode() == opcode) {
                indexes.add(((VarInsnNode) instruction).var);
            }
        }
        return indexes;
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int position = 0;
        while ((position = value.indexOf(needle, position)) >= 0) {
            count++;
            position += needle.length();
        }
        return count;
    }

    private String generatedFunction(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(__ngen_[A-Za-z0-9_]+)\\(JNIEnv").matcher(body);
        assertTrue(matcher.find(), "generated IR function not found");
        return matcher.group(1);
    }

    private void compileAndRunCppHarness(
            String directoryPrefix, String source, String runtimeFailure)
            throws Exception {
        Path gpp = executableOnPath("g++");
        Path javaHome = Paths.get(System.getProperty("java.home"));
        Path jniInclude = javaHome.resolve("include");
        Path platformInclude = jniInclude.resolve(jniPlatformDirectory());
        assertTrue(gpp != null, "g++ is required for the IR runtime test");
        assertTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are required for the IR runtime test");
        assertTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are required for the IR runtime test");

        Path directory = Files.createTempDirectory(directoryPrefix);
        Path sourceFile = directory.resolve("harness.cpp");
        Path binary = directory.resolve("harness");
        Path compilerOutput = directory.resolve("gpp-output.txt");
        Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
        Process compileProcess = new ProcessBuilder(gpp.toString(), "-std=c++17",
                "-I" + jniInclude, "-I" + platformInclude,
                sourceFile.toString(), "-o", binary.toString())
                .redirectErrorStream(true)
                .redirectOutput(compilerOutput.toFile())
                .start();
        int compileExit = compileProcess.waitFor();
        String output = new String(Files.readAllBytes(compilerOutput),
                StandardCharsets.UTF_8);
        assertEquals(0, compileExit,
                "g++ failed:\n" + output + "\nSource:\n" + source);

        Process runProcess = new ProcessBuilder(binary.toString()).start();
        assertEquals(0, runProcess.waitFor(), runtimeFailure);
    }

    private String arrayJniType(String carrier) {
        return "j" + carrier.toLowerCase(Locale.ROOT);
    }

    private byte[] writeClass(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void createInterfaceConstantDynamicJar(
            Path jarPath, String interfaceName, String counterName,
            String mainName) throws IOException {
        ClassNode owner =
                interfaceConstantDynamicOwner(interfaceName, counterName);
        owner.methods.add(interfaceConstantDynamicStringMethod(interfaceName));
        owner.methods.add(interfaceConstantDynamicIntMethod(interfaceName));
        ClassNode counter = interfaceConstantDynamicCounter(counterName);

        ClassNode main = new ClassNode(Opcodes.ASM9);
        main.version = Opcodes.V11;
        main.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        main.name = mainName;
        main.superName = "java/lang/Object";
        MethodNode entry = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, interfaceName, "stringValue",
                "()Ljava/lang/String;", true));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false));
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, interfaceName, "intValue",
                "()I", true));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(I)V", false));
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, counterName, "stringCalls", "I"));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(I)V", false));
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System", "out",
                "Ljava/io/PrintStream;"));
        entry.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, counterName, "intCalls", "I"));
        entry.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println",
                "(I)V", false));
        entry.instructions.add(new InsnNode(Opcodes.RETURN));
        entry.maxLocals = 1;
        entry.maxStack = 2;
        main.methods.add(entry);

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, main.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            for (ClassNode classNode : Arrays.asList(owner, counter, main)) {
                output.putNextEntry(new JarEntry(classNode.name + ".class"));
                output.write(writeClass(classNode));
                output.closeEntry();
            }
        }
    }

    private void createPrimitiveClassLdcJar(
            Path jarPath, String ownerName, boolean primitiveLdc) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.methods.add(primitiveClassAccessor(
                "primitiveInt", Type.INT_TYPE, "java/lang/Integer", primitiveLdc));
        owner.methods.add(primitiveClassAccessor(
                "primitiveLong", Type.LONG_TYPE, "java/lang/Long", primitiveLdc));
        owner.methods.add(primitiveClassAccessor(
                "primitiveBoolean", Type.BOOLEAN_TYPE, "java/lang/Boolean", primitiveLdc));
        owner.methods.add(primitiveClassAccessor(
                "primitiveVoid", Type.VOID_TYPE, "java/lang/Void", primitiveLdc));
        owner.methods.add(primitiveClassMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private String assertFieldRoundTrip(MethodNode method, IrType expectedType,
                                        String getAccessor, String setAccessor) {
        IrMethod ir = frontend.build("example/Math", method);
        IrInstruction get = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(instruction -> instruction instanceof IrNodes.GetField
                        || instruction instanceof IrNodes.GetStaticField)
                .findFirst().orElseThrow(AssertionError::new);
        IrInstruction put = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(instruction -> instruction instanceof IrNodes.PutField
                        || instruction instanceof IrNodes.PutStaticField)
                .findFirst().orElseThrow(AssertionError::new);
        IrValue putValue = put instanceof IrNodes.PutField
                ? ((IrNodes.PutField) put).getValue()
                : ((IrNodes.PutStaticField) put).getValue();
        assertEquals(expectedType, get.getResult().getType());
        assertEquals(expectedType, putValue.getType());

        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        String cpp = context.output.toString();
        assertTrue(cpp.contains("env->" + getAccessor));
        assertTrue(cpp.contains("env->" + setAccessor));
        assertEquals(1, obfuscator.getCachedFields().size());
        return cpp;
    }

    private void assertSmallPrimitiveFieldRoundTrip(String fieldName, String descriptor,
                                                    String carrier, String jniType,
                                                    int value, boolean staticField) {
        MethodNode method = primitiveFieldConstantRoundTripMethod(
                fieldName, descriptor, value, staticField);
        String cpp = assertFieldRoundTrip(method, IrType.I32,
                "Get" + (staticField ? "Static" : "") + carrier + "Field",
                "Set" + (staticField ? "Static" : "") + carrier + "Field");
        assertTrue(cpp.contains("= " + value + ";"));
        assertTrue(cpp.contains("(jint) env->Get"
                + (staticField ? "Static" : "") + carrier + "Field"));
        assertTrue(cpp.contains("(" + jniType + ") "));
        if ("Z".equals(descriptor)) {
            assertTrue(cpp.contains("& 1"));
        }
        assertFalse(cpp.contains("IntField"));
    }

    private void assertSmallPrimitiveInvoke(MethodNode method, IrNodes.Invoke.Kind kind,
                                            String callMethod, String jniType,
                                            String argumentName) {
        IrNodes.Invoke invoke = onlyInvoke(frontend.build("example/Math", method));
        assertEquals(kind, invoke.getKind());
        assertEquals(IrType.I32, invoke.getArguments().get(0).getType());
        assertEquals(IrType.I32, invoke.getResult().getType());

        String cpp = compileToCpp(method, 0);
        assertTrue(cpp.contains("(jint) env->" + callMethod));
        assertTrue(cpp.contains("(" + jniType + ") "));
        assertTrue(cpp.contains(argumentName));
        if ("jboolean".equals(jniType)) {
            assertTrue(cpp.contains("& 1"));
        }
        String intCallMethod = callMethod.replace("Boolean", "Int")
                .replace("Byte", "Int").replace("Char", "Int").replace("Short", "Int");
        assertFalse(cpp.contains("env->" + intCallMethod + "("));
    }

    private void assertFloatingInvoke(MethodNode method, IrNodes.Invoke.Kind kind,
                                      IrType type, String callMethod) {
        IrNodes.Invoke invoke = onlyInvoke(frontend.build("example/Math", method));
        assertEquals(kind, invoke.getKind());
        assertEquals(type, invoke.getArguments().get(0).getType());
        assertEquals(type, invoke.getResult().getType());

        String cpp = compileToCpp(method, 0);
        assertTrue(cpp.contains("env->" + callMethod + "("));
        assertFalse(cpp.contains("env->"
                + callMethod.replace("Float", "Int").replace("Double", "Long") + "("));
    }

    private void assertFloatingFieldConstant(String name, String descriptor, IrType type,
                                             String carrier, Number value,
                                             boolean staticField) {
        MethodNode method = floatingFieldConstantRoundTripMethod(
                name, descriptor, value, staticField);
        IrMethod ir = frontend.build("example/Math", method);
        IrInstruction constant = ir.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(instruction -> instruction instanceof IrNodes.FloatConst
                        || instruction instanceof IrNodes.DoubleConst)
                .findFirst().orElseThrow(AssertionError::new);
        String expectedBits;
        if (type == IrType.F32) {
            int bits = Float.floatToRawIntBits(value.floatValue());
            assertEquals(bits, ((IrNodes.FloatConst) constant).getRawBits());
            expectedBits = String.format(Locale.ROOT, "0x%08xU", bits);
        } else {
            long bits = Double.doubleToRawLongBits(value.doubleValue());
            assertEquals(bits, ((IrNodes.DoubleConst) constant).getRawBits());
            expectedBits = String.format(Locale.ROOT, "0x%016xULL", bits);
        }

        String cpp = assertFieldRoundTrip(method, type,
                "Get" + (staticField ? "Static" : "") + carrier + "Field",
                "Set" + (staticField ? "Static" : "") + carrier + "Field");
        assertTrue(cpp.contains(expectedBits));
        assertTrue(cpp.contains(type.getCppType()));
        assertFalse(cpp.contains("env->Get"
                + (staticField ? "Static" : "")
                + ("Float".equals(carrier) ? "Int" : "Long") + "Field"));
    }

    private IrPhi onlyStackPhi(IrMethod method) {
        return method.getBlocks().stream()
                .flatMap(block -> block.getPhis().stream())
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK)
                .findFirst().orElseThrow(AssertionError::new);
    }

    private void assertArrayExceptionEdge(
            IrMethod method, Class<? extends IrInstruction> instructionType,
            String catchType) {
        IrBlock block = method.getBlocks().stream()
                .filter(candidate -> candidate.getInstructions().stream()
                        .anyMatch(instructionType::isInstance))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, block.getExceptionEdges().size());
        assertEquals(catchType, block.getExceptionEdges().get(0).getCatchType());
    }

    private String compileToCpp(MethodNode method, int methodId) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, methodId, owner(), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        return context.output.toString();
    }

    private IrNodes.Invoke onlyInvoke(IrMethod method) {
        return method.getBlocks().stream()
                .flatMap(block -> block.getInstructions().stream())
                .filter(IrNodes.Invoke.class::isInstance)
                .map(IrNodes.Invoke.class::cast)
                .findFirst().orElseThrow(AssertionError::new);
    }

    private Class<?> rejectConstructorReceiverWrite(
            String ownerName, MethodNode constructor) {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.methods.add(constructor);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context =
                new MethodContext(obfuscator, constructor, 0, owner, 0);
        int instructionCount = constructor.instructions.size();
        java.util.List<Integer> opcodes = realOpcodes(constructor);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.ASTORE, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor prefix ASTORE 0 does not provably preserve "
                        + "the constructor receiver"));
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
        return new ByteArrayClassLoader().define(writeClass(owner));
    }

    private void createPrefixExtraLocalJar(
            Path jarPath, String ownerName, boolean gapped) throws IOException {
        ClassNode owner = constructorOwner(
                ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        owner.methods.add(gapped
                ? gappedPrefixExtraReferenceConstructor(owner.name)
                : prefixExtraReferenceConstructor(owner.name));
        owner.methods.add(prefixExtraReferenceMain(owner.name));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createIdentityAstoreZeroJar(
            Path jarPath, String ownerName) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        owner.methods.add(identityAstoreZeroConstructor(owner.name));
        owner.methods.add(identityAstoreZeroMain(owner.name));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createPrefixOnlyTryCatchJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                prefixOnlyTryCatchConstructor(ownerName, baseName));
        owner.methods.add(prefixOnlyTryCatchMain(ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createRelocatedPrefixReturnHandlerJar(
            Path jarPath, String ownerName) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                suffixTryCatchWithPrefixReturnHandler(ownerName));
        owner.methods.add(relocatedPrefixReturnHandlerMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createRelocatedPrefixGotoReturnHandlerJar(
            Path jarPath, String ownerName) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                suffixTryCatchWithPrefixGotoReturnHandler(ownerName));
        owner.methods.add(relocatedPrefixReturnHandlerMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createRelocatedPrefixAstoreReturnHandlerJar(
            Path jarPath, String ownerName) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                suffixTryCatchWithPrefixAstoreReturnHandler(ownerName));
        owner.methods.add(relocatedPrefixReturnHandlerMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createRelocatedPrefixAthrowHandlerJar(
            Path jarPath, String ownerName) throws IOException {
        ClassNode owner = constructorOwner(ownerName, "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                suffixTryCatchWithPrefixAthrowHandler(ownerName));
        owner.methods.add(relocatedPrefixAthrowHandlerMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperDiamondJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        owner.methods.add(
                multipleSuperDiamondConstructor(ownerName, baseName));
        owner.methods.add(multipleSuperMain(ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createPostChainConditionalBranchJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(postChainConditionalBranchConstructor(
                ownerName, baseName));
        owner.methods.add(postChainConditionalBranchMain(
                ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createPostChainIntCompareFamilyJar(
            Path jarPath, String ownerPrefix, String baseName,
            String mainName) throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        java.util.List<ClassNode> owners = new java.util.ArrayList<>();
        for (int opcode : POST_CHAIN_INT_COMPARE_OPCODES) {
            ClassNode owner = constructorOwner(
                    ownerPrefix + opcode, baseName);
            owner.version = Opcodes.V1_8;
            owner.fields.add(new FieldNode(
                    Opcodes.ACC_PUBLIC, "result", "I", null, null));
            owner.methods.add(postChainIntCompareConstructor(
                    owner.name, baseName, opcode));
            owners.add(owner);
        }

        ClassNode main = constructorOwner(mainName, "java/lang/Object");
        main.version = Opcodes.V1_8;
        main.methods.add(postChainIntCompareFamilyMain(ownerPrefix));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, main.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            for (ClassNode owner : owners) {
                output.putNextEntry(new JarEntry(owner.name + ".class"));
                output.write(writeClass(owner));
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry(main.name + ".class"));
            output.write(writeClass(main));
            output.closeEntry();
        }
    }

    private void createPostChainSwitchJar(
            Path jarPath, String tableOwnerName, String lookupOwnerName,
            String baseName, String mainName) throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode tableOwner = constructorOwner(tableOwnerName, baseName);
        tableOwner.version = Opcodes.V1_8;
        tableOwner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        tableOwner.methods.add(postChainSwitchConstructor(
                tableOwnerName, baseName, false));
        ClassNode lookupOwner = constructorOwner(lookupOwnerName, baseName);
        lookupOwner.version = Opcodes.V1_8;
        lookupOwner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        lookupOwner.methods.add(postChainSwitchConstructor(
                lookupOwnerName, baseName, true));
        ClassNode main = constructorOwner(mainName, "java/lang/Object");
        main.version = Opcodes.V1_8;
        main.methods.add(postChainSwitchMain(
                tableOwnerName, lookupOwnerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, main.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            for (ClassNode classNode :
                    Arrays.asList(base, tableOwner, lookupOwner, main)) {
                output.putNextEntry(new JarEntry(classNode.name + ".class"));
                output.write(writeClass(classNode));
                output.closeEntry();
            }
        }
    }

    private void createConditionalBridgeExtraJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        owner.methods.add(conditionalPrefixExitExtraConstructor(
                ownerName, baseName));
        owner.methods.add(conditionalBridgeExtraMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperDifferentSuffixJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                multipleSuperDifferentSuffixConstructor(
                        ownerName, baseName));
        owner.methods.add(twoDifferentSuffixMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createTwoDistinctSuffixExtraJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                twoDistinctSuffixesWithFieldExtraConstructor(
                        ownerName, baseName));
        owner.methods.add(twoDifferentSuffixMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeDistinctSuffixJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "result", "I", null, null));
        owner.methods.add(
                multipleSuperThreeDistinctSuffixConstructor(
                        ownerName, baseName));
        owner.methods.add(threeDistinctSuffixMain(ownerName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperIdenticalSuffixCopiesJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "original", "I", null, null));
        owner.methods.add(
                multipleSuperIdenticalSuffixCopiesConstructor(
                        ownerName, baseName));
        owner.methods.add(multipleSuperMain(ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperSeparateReturnsJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperSeparateReturnsConstructor(baseName));
        owner.methods.add(
                multipleSuperSeparateReturnsMain(ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeSeparateReturnsJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperThreeSeparateReturnsConstructor(baseName));
        owner.methods.add(
                multipleSuperThreeSeparateReturnsMain(
                        ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeIsubImulReturnsJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperThreeIsubImulReturnsConstructor(baseName));
        owner.methods.add(
                multipleSuperThreeSeparateReturnsMain(
                        ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeBitwiseReturnsJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperThreeBitwiseReturnsConstructor(baseName));
        owner.methods.add(
                multipleSuperThreeSeparateReturnsMain(
                        ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeShiftReturnsJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperThreeShiftReturnsConstructor(baseName));
        owner.methods.add(
                multipleSuperThreeSeparateReturnsMain(
                        ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createMultipleSuperThreeIdenticalSuffixCopiesJar(
            Path jarPath, String ownerName, String baseName)
            throws IOException {
        ClassNode base = multipleSuperBase(baseName);
        base.version = Opcodes.V1_8;
        ClassNode owner = constructorOwner(ownerName, baseName);
        owner.version = Opcodes.V1_8;
        owner.methods.add(
                multipleSuperThreeIdenticalSuffixCopiesConstructor(baseName));
        owner.methods.add(
                multipleSuperThreeSeparateReturnsMain(
                        ownerName, baseName));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(
                             Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(base.name + ".class"));
            output.write(writeClass(base));
            output.closeEntry();
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private void createReferenceParameterAstoreJar(Path jarPath) throws IOException {
        ClassNode owner = constructorOwner(
                "example/FlexCtorAstore", "java/lang/Object");
        owner.version = Opcodes.V1_8;
        owner.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "result",
                "Ljava/lang/String;", null, null));
        owner.methods.add(referenceParameterAstoreResultConstructor(owner.name));
        owner.methods.add(referenceParameterAstoreMain(owner.name));

        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(
                Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(
                Attributes.Name.MAIN_CLASS, owner.name.replace('/', '.'));
        try (JarOutputStream output =
                     new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            output.putNextEntry(new JarEntry(owner.name + ".class"));
            output.write(writeClass(owner));
            output.closeEntry();
        }
    }

    private Path javaExecutable() {
        return Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe" : "java");
    }

    private void assertInvokeRejectedBeforeMutation(MethodNode method, int opcode) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));
        assertEquals(opcode, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    private void assertUnchangedAfterRejectedIr(MethodNode method, MethodContext context,
                                                NativeObfuscator obfuscator) {
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
    }

    private void assertStackShuffle(String name, int opcode, Type[] inputs,
                                    int... expectedParameterIndexes) {
        assertStackShuffle(name, new int[]{opcode}, inputs, expectedParameterIndexes);
    }

    private void assertStackShuffle(String name, int[] opcodes, Type[] inputs,
                                    int... expectedParameterIndexes) {
        MethodNode method = stackShuffleMethod(name, opcodes, inputs,
                expectedParameterIndexes);
        IrMethod ir = frontend.build("example/Math", method);
        IrBlock entry = ir.getBlocks().get(0);
        IrBlock join = ir.getBlocks().get(1);
        List<IrPhi> stackPhis = join.getPhis().stream()
                .filter(phi -> phi.getSlotKind() == IrPhi.SlotKind.STACK)
                .collect(Collectors.toList());

        assertEquals(expectedParameterIndexes.length, stackPhis.size(), name);
        int slot = 0;
        for (int i = 0; i < expectedParameterIndexes.length; i++) {
            IrValue expected = ir.getParameters().get(expectedParameterIndexes[i]);
            IrPhi phi = stackPhis.get(i);
            assertEquals(slot, phi.getSlotIndex(), name + " stack slot " + i);
            assertEquals(expected.getType(), phi.getResult().getType(),
                    name + " carrier " + i);
            assertEquals(expected, phi.getIncoming().get(entry),
                    name + " SSA value " + i);
            slot += expected.getType().getJvmSlots();
        }

        String cpp = emitter.emitBody(ir);
        assertFalse(cpp.contains("env->"), name + " must be a pure SSA reorder");
    }

    private void assertIllegalStackShuffle(int opcode, Type... inputs) {
        MethodNode method = stackShuffleMethod("illegalStack" + opcode,
                new int[]{opcode}, inputs, new int[0]);
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(opcode, error.getOpcode());
        assertTrue(error.getMessage().contains("legal JVM form"));
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
    }

    private MethodNode stackShuffleMethod(String name, int[] opcodes, Type[] inputs,
                                          int[] expectedParameterIndexes) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, Type.getMethodDescriptor(Type.VOID_TYPE, inputs), null, null);
        int local = 0;
        int inputSlots = 0;
        for (Type input : inputs) {
            method.instructions.add(new VarInsnNode(input.getOpcode(Opcodes.ILOAD), local));
            local += input.getSize();
            inputSlots += input.getSize();
        }
        for (int opcode : opcodes) {
            method.instructions.add(new InsnNode(opcode));
        }
        LabelNode join = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(join);
        for (int i = expectedParameterIndexes.length - 1; i >= 0; i--) {
            Type output = inputs[expectedParameterIndexes[i]];
            method.instructions.add(new InsnNode(
                    output.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = local;
        method.maxStack = inputSlots + 2;
        return method;
    }

    private MethodNode incrementFieldMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, 0,
                "increment", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 3;
        return method;
    }

    private MethodNode staticIntFieldMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "setAndGetCounter", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", "counter", "I"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "counter", "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode instanceFieldRoundTripMethod(String name, String descriptor,
                                                    int loadOpcode, int returnOpcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(Lexample/Math;" + descriptor + ")" + descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(loadOpcode, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", name, descriptor));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", name, descriptor));
        method.instructions.add(new InsnNode(returnOpcode));
        boolean wide = "J".equals(descriptor) || "D".equals(descriptor);
        method.maxLocals = wide ? 3 : 2;
        method.maxStack = wide ? 3 : 2;
        return method;
    }

    private MethodNode staticFieldRoundTripMethod(String name, String descriptor,
                                                  int loadOpcode, int returnOpcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(" + descriptor + ")" + descriptor, null, null);
        method.instructions.add(new VarInsnNode(loadOpcode, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", name, descriptor));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", name, descriptor));
        method.instructions.add(new InsnNode(returnOpcode));
        boolean wide = "J".equals(descriptor) || "D".equals(descriptor);
        method.maxLocals = wide ? 2 : 1;
        method.maxStack = wide ? 2 : 1;
        return method;
    }

    private MethodNode primitiveFieldConstantRoundTripMethod(
            String name, String descriptor, int value, boolean staticField) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, staticField ? "()I" : "(Lexample/Math;)I", null, null);
        if (!staticField) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        method.instructions.add(new IntInsnNode(Opcodes.SIPUSH, value));
        method.instructions.add(new FieldInsnNode(
                staticField ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD,
                "example/Math", name, descriptor));
        if (!staticField) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        method.instructions.add(new FieldInsnNode(
                staticField ? Opcodes.GETSTATIC : Opcodes.GETFIELD,
                "example/Math", name, descriptor));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = staticField ? 0 : 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode floatingFieldConstantRoundTripMethod(
            String name, String descriptor, Number value, boolean staticField) {
        int returnOpcode = "F".equals(descriptor) ? Opcodes.FRETURN : Opcodes.DRETURN;
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, staticField ? "()" + descriptor
                : "(Lexample/Math;)" + descriptor, null, null);
        if (!staticField) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        method.instructions.add(new LdcInsnNode(value));
        method.instructions.add(new FieldInsnNode(
                staticField ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD,
                "example/Math", name, descriptor));
        if (!staticField) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        method.instructions.add(new FieldInsnNode(
                staticField ? Opcodes.GETSTATIC : Opcodes.GETFIELD,
                "example/Math", name, descriptor));
        method.instructions.add(new InsnNode(returnOpcode));
        method.maxLocals = staticField ? 0 : 1;
        method.maxStack = "D".equals(descriptor) ? 3 : 2;
        return method;
    }

    private MethodNode nullReceiverGetFieldMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "nullReceiverGetField", "(Lexample/Math;)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "longValue", "J"));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode nullReceiverPutFieldMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "nullReceiverPutField",
                "(Lexample/Math;Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "objectValue", "Ljava/lang/Object;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedPrimitiveFieldMethod(String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedPrimitiveField", "()V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "unsupported", descriptor));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode staticInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "twice", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "add", "(II)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode preprocessorLocalsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "preprocessorLocals", "()V", null, null);
        method.instructions.add(PreprocessorUtils.LOOKUP_LOCAL.get());
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(PreprocessorUtils.CLASSLOADER_LOCAL.get());
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(PreprocessorUtils.CLASS_LOCAL.get());
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode linkCallSiteMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "linkCallSite", "()V", null, null);
        for (int i = 0; i < 6; i++) {
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        method.instructions.add(PreprocessorUtils.LINK_CALL_SITE_METHOD.get());
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 6;
        return method;
    }

    private MethodNode invokeReverseMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "invokeReverse", "(Ljava/lang/invoke/MethodHandle;I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(PreprocessorUtils.INVOKE_REVERSE.apply("(I)I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode methodHandleInvokeExactMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "invokeExact", "(Ljava/lang/invoke/MethodHandle;I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandle", "invokeExact", "(I)I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode typeDescriptorBootstrapMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "typeDescriptorBootstrap", "()Ljava/lang/Object;", null, null);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "example/Bootstrap",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/invoke/TypeDescriptor;)"
                        + "Ljava/lang/invoke/CallSite;",
                false);
        method.instructions.add(new InvokeDynamicInsnNode(
                "dynamic", "()Ljava/lang/Object;", bootstrap));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode staticPrimitiveInvokeMethod(String wrapperName, String targetName,
                                                   String targetDescriptor) {
        Type argumentType = Type.getArgumentTypes(targetDescriptor)[0];
        Type returnType = Type.getReturnType(targetDescriptor);
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                wrapperName, targetDescriptor, null, null);
        method.instructions.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD), 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/PrimitiveTarget", targetName, targetDescriptor, false));
        method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = argumentType.getSize();
        method.maxStack = argumentType.getSize();
        return method;
    }

    private MethodNode virtualInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "identity", "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode virtualPrimitiveInvokeMethod(String wrapperName, String targetName,
                                                    String targetDescriptor) {
        String targetOwner = "example/PrimitiveTarget";
        Type argumentType = Type.getArgumentTypes(targetDescriptor)[0];
        Type returnType = Type.getReturnType(targetDescriptor);
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                wrapperName, Type.getMethodDescriptor(returnType,
                Type.getObjectType(targetOwner), argumentType), null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(argumentType.getOpcode(Opcodes.ILOAD), 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                targetOwner, targetName, targetDescriptor, false));
        method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = 1 + argumentType.getSize();
        method.maxStack = 1 + argumentType.getSize();
        return method;
    }

    private MethodNode interfaceInvokeMethod(String wrapperName, String targetName,
                                             String targetDescriptor) {
        String targetOwner = "example/Phase11Interface";
        Type returnType = Type.getReturnType(targetDescriptor);
        Type[] targetArguments = Type.getArgumentTypes(targetDescriptor);
        Type[] wrapperArguments = new Type[targetArguments.length + 1];
        wrapperArguments[0] = Type.getObjectType(targetOwner);
        System.arraycopy(targetArguments, 0, wrapperArguments, 1, targetArguments.length);
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                wrapperName, Type.getMethodDescriptor(returnType, wrapperArguments),
                null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int local = 1;
        int argumentSlots = 0;
        for (Type argument : targetArguments) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD),
                    local));
            local += argument.getSize();
            argumentSlots += argument.getSize();
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                targetOwner, targetName, targetDescriptor, true));
        method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = local;
        method.maxStack = 1 + argumentSlots;
        return method;
    }

    private MethodNode specialInvokeMethod(String wrapperName, String targetOwner,
                                           String targetName, String targetDescriptor) {
        Type returnType = Type.getReturnType(targetDescriptor);
        Type[] targetArguments = Type.getArgumentTypes(targetDescriptor);
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                wrapperName, targetDescriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int local = 1;
        int argumentSlots = 0;
        for (Type argument : targetArguments) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD),
                    local));
            local += argument.getSize();
            argumentSlots += argument.getSize();
        }
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                targetOwner, targetName, targetDescriptor, false));
        method.instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = local;
        method.maxStack = 1 + argumentSlots;
        return method;
    }

    private MethodNode nullableInterfaceInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "nullableInterface", "(Lexample/Phase11Interface;)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "example/Phase11Interface", "size", "()I", true));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NullPointerException"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unsupportedInvokeDescriptorMethod(int opcode,
                                                         String targetDescriptor) {
        Type[] targetArguments = Type.getArgumentTypes(targetDescriptor);
        boolean staticInvoke = opcode == Opcodes.INVOKESTATIC;
        Type[] wrapperArguments = new Type[targetArguments.length + (staticInvoke ? 0 : 1)];
        if (!staticInvoke) {
            wrapperArguments[0] = Type.getObjectType("example/Phase11Interface");
        }
        for (int i = 0; i < targetArguments.length; i++) {
            wrapperArguments[i + (staticInvoke ? 0 : 1)] =
                    targetArguments[i].getSize() == 2
                    ? Type.LONG_TYPE : Type.INT_TYPE;
        }
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedInvoke",
                Type.getMethodDescriptor(Type.VOID_TYPE, wrapperArguments), null, null);
        int local = 0;
        int argumentSlots = 0;
        if (!staticInvoke) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, local++));
            argumentSlots++;
        }
        for (int i = 0; i < targetArguments.length; i++) {
            Type wrapperArgument = wrapperArguments[i + (staticInvoke ? 0 : 1)];
            method.instructions.add(new VarInsnNode(
                    wrapperArgument.getOpcode(Opcodes.ILOAD), local));
            local += wrapperArgument.getSize();
            argumentSlots += wrapperArgument.getSize();
        }
        method.instructions.add(new MethodInsnNode(opcode,
                opcode == Opcodes.INVOKEINTERFACE
                        ? "example/Phase11Interface"
                        : staticInvoke ? "example/PrimitiveTarget" : "example/Base",
                "unsupported", targetDescriptor,
                opcode == Opcodes.INVOKEINTERFACE));
        Type returnType = Type.getReturnType(targetDescriptor);
        if (returnType.getSort() != Type.VOID) {
            method.instructions.add(new InsnNode(
                    returnType.getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = local;
        method.maxStack = Math.max(argumentSlots, returnType.getSize());
        return method;
    }

    private MethodNode invokedynamicMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "dynamic", "()I", null, null);
        appendUnsupportedInvokeDynamic(method, "()I");
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode stringConcatIndyMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "concat", "(Ljava/lang/String;)Ljava/lang/String;", null, null);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC,
                "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/invoke/MethodType;Ljava/lang/String;"
                        + "[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;", false);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InvokeDynamicInsnNode("makeConcatWithConstants",
                "(Ljava/lang/String;)Ljava/lang/String;", bootstrap, "prefix-\u0001"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 8;
        return method;
    }

    private void appendUnsupportedInvokeDynamic(MethodNode method, String descriptor) {
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "example/Bootstrap",
                "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                false);
        method.instructions.add(new InvokeDynamicInsnNode(
                "dynamic", descriptor, bootstrap));
    }

    // INVOKEDYNAMIC and the proven ConstantDynamic shapes are admitted by the
    // IR frontend. Legacy JSR/RET subroutines remain deliberately unsupported
    // and provide the reject-before-mutation sentinel.
    private void appendStillUnsupportedConstruct(MethodNode method) {
        LabelNode subroutine = new LabelNode();
        LabelNode continuation = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.JSR, subroutine));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, continuation));
        method.instructions.add(subroutine);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.RET, 0));
        method.instructions.add(continuation);
    }

    private MethodNode stringLdcMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "ldcStrings", "()I", null, null);
        method.instructions.add(new LdcInsnNode(""));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 0));
        method.instructions.add(new LdcInsnNode("ascii"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "consumeString", "(Ljava/lang/String;)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new LdcInsnNode("héllo世界"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new LdcInsnNode("nul\0inside"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode classLdcMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "ldcClasses", "()Ljava/lang/Class;", null, null);
        method.instructions.add(new LdcInsnNode(Type.getObjectType("java/lang/String")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(Type.getObjectType("example/Fixture")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(Type.getType("[I")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(Type.getType("[Ljava/lang/String;")));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode longLdcMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "ldcLongs", "()J", null, null);
        method.instructions.add(new LdcInsnNode(0x1_0000_0000L));
        method.instructions.add(new LdcInsnNode(-1L));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 0;
        method.maxStack = 4;
        return method;
    }

    private MethodNode primitiveClassLdcMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "primitiveClasses", "()Ljava/lang/Class;", null, null);
        Type[] constants = {
                Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.DOUBLE_TYPE,
                Type.BOOLEAN_TYPE, Type.BYTE_TYPE, Type.SHORT_TYPE, Type.CHAR_TYPE,
                Type.VOID_TYPE
        };
        for (int index = 0; index < constants.length; index++) {
            method.instructions.add(new LdcInsnNode(constants[index]));
            if (index + 1 < constants.length) {
                method.instructions.add(new InsnNode(Opcodes.POP));
            }
        }
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode primitiveClassAccessor(
            String name, Type primitive, String wrapperOwner, boolean primitiveLdc) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                name, "()Ljava/lang/Class;", null, null);
        if (primitiveLdc) {
            method.instructions.add(new LdcInsnNode(primitive));
        } else {
            method.instructions.add(new FieldInsnNode(
                    Opcodes.GETSTATIC, wrapperOwner, "TYPE", "Ljava/lang/Class;"));
        }
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode primitiveClassMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendPrimitiveClassPrint(
                method, owner, "primitiveInt", "java/lang/Integer");
        appendPrimitiveClassPrint(
                method, owner, "primitiveLong", "java/lang/Long");
        appendPrimitiveClassPrint(
                method, owner, "primitiveBoolean", "java/lang/Boolean");
        appendPrimitiveClassPrint(
                method, owner, "primitiveVoid", "java/lang/Void");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 3;
        return method;
    }

    private void appendPrimitiveClassPrint(
            MethodNode method, String owner, String accessor, String wrapperOwner) {
        LabelNode different = new LabelNode();
        LabelNode identityReady = new LabelNode();
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, accessor,
                "()Ljava/lang/Class;", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, wrapperOwner, "TYPE", "Ljava/lang/Class;"));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, different));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, identityReady));
        method.instructions.add(different);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(identityReady);
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(Z)V", false));

        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, owner, accessor,
                "()Ljava/lang/Class;", false));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                "getName", "()Ljava/lang/String;", false));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(Ljava/lang/String;)V", false));
    }

    private MethodNode rawMethodConstantsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "rawMethodConstants", "()V", null, null);
        method.instructions.add(new LdcInsnNode(""));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(Type.getObjectType("java/lang/String")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(0x1_0000_0000L));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 0));
        method.instructions.add(new LdcInsnNode(new Handle(Opcodes.H_INVOKESTATIC,
                "example/Bootstrap", "target", "()V", false)));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(
                Type.getMethodType("(Ljava/lang/String;I)V")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode constructObjectMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "constructObject", "()I", null, null);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode intFieldConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode floatingFieldConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(FD)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "floatValue", "F"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "doubleValue", "D"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 4;
        method.maxStack = 3;
        return method;
    }

    private MethodNode intFieldGetter() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "getValue", "()I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode subclassConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(IJ)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "example/Base", "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Child", "value", "J"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 4;
        method.maxStack = 3;
        return method;
    }

    private MethodNode referenceFieldConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/Object;[I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Holder", "objectValue", "Ljava/lang/Object;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Holder", "arrayValue", "[I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode delegatingConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "example/Math", "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "otherValue", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private ClassNode multipleSuperBase(String name) {
        ClassNode base = constructorOwner(name, "java/lang/Object");
        base.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC, "magnitude", "I", null, null));
        MethodNode constructor = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        constructor.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, name, "magnitude", "I"));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxLocals = 2;
        constructor.maxStack = 2;
        base.methods.add(constructor);
        return base;
    }

    private MethodNode multipleSuperDiamondConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "original", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode postChainConditionalBranchConstructor(
            String owner, String superName) {
        return postChainIntCompareConstructor(
                owner, superName, Opcodes.IFNE);
    }

    private MethodNode postChainIntCompareConstructor(
            String owner, String superName, int compareOpcode) {
        boolean binaryCompare = isBinaryIntCompare(compareOpcode);
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", binaryCompare ? "(III)V" : "(II)V", null, null);
        LabelNode secondCall = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, secondCall));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        if (binaryCompare) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        }
        method.instructions.add(new JumpInsnNode(compareOpcode, join));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(secondCall);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 41));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = binaryCompare ? 4 : 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode postChainSwitchConstructor(
            String owner, String superName, boolean lookup) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(II)V", null, null);
        LabelNode secondCall = new LabelNode();
        LabelNode joinTrampoline = new LabelNode();
        LabelNode earlyReturn = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, secondCall));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        if (lookup) {
            method.instructions.add(new LookupSwitchInsnNode(
                    earlyReturn, new int[]{7, 42},
                    new LabelNode[]{join, joinTrampoline}));
        } else {
            method.instructions.add(new TableSwitchInsnNode(
                    0, 1, earlyReturn, join, joinTrampoline));
        }
        method.instructions.add(joinTrampoline);
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(earlyReturn);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(secondCall);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 41));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private void addComputedPostChainSwitchKey(MethodNode constructor) {
        AbstractInsnNode switchInstruction = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(instruction ->
                        instruction instanceof TableSwitchInsnNode
                                || instruction instanceof LookupSwitchInsnNode)
                .findFirst().orElseThrow(AssertionError::new);
        InsnList computation = new InsnList();
        computation.add(new InsnNode(Opcodes.ICONST_1));
        computation.add(new InsnNode(Opcodes.IADD));
        constructor.instructions.insertBefore(switchInstruction, computation);
    }

    private void addWorkBeforePostChainSwitchReturn(MethodNode constructor) {
        AbstractInsnNode prefixReturn = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(instruction ->
                        instruction.getOpcode() == Opcodes.RETURN)
                .findFirst().orElseThrow(AssertionError::new);
        InsnList work = new InsnList();
        work.add(new InsnNode(Opcodes.ICONST_0));
        work.add(new InsnNode(Opcodes.POP));
        constructor.instructions.insertBefore(prefixReturn, work);
    }

    private void addPostChainSwitchExceptionTable(MethodNode constructor) {
        MethodInsnNode firstCall = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL)
                .findFirst().orElseThrow(AssertionError::new);
        AbstractInsnNode switchInstruction = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(instruction ->
                        instruction instanceof TableSwitchInsnNode
                                || instruction instanceof LookupSwitchInsnNode)
                .findFirst().orElseThrow(AssertionError::new);
        AbstractInsnNode prefixReturn = Arrays.stream(
                        constructor.instructions.toArray())
                .filter(instruction ->
                        instruction.getOpcode() == Opcodes.RETURN)
                .findFirst().orElseThrow(AssertionError::new);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        constructor.instructions.insert(firstCall, start);
        constructor.instructions.insertBefore(switchInstruction, end);
        InsnList handlerBody = new InsnList();
        handlerBody.add(handler);
        handlerBody.add(new InsnNode(Opcodes.POP));
        handlerBody.add(new InsnNode(Opcodes.RETURN));
        constructor.instructions.insert(prefixReturn, handlerBody);
        constructor.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));
    }

    private MethodNode switchSkippingChainConstructor(boolean lookup) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode chainCall = new LabelNode();
        LabelNode suffix = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        if (lookup) {
            method.instructions.add(new LookupSwitchInsnNode(
                    chainCall, new int[]{0}, new LabelNode[]{suffix}));
        } else {
            method.instructions.add(new TableSwitchInsnNode(
                    0, 0, chainCall, suffix));
        }
        method.instructions.add(chainCall);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(suffix);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode conditionalPrefixExitExtraConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode assigned = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, assigned));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(assigned);
        method.instructions.add(new LdcInsnNode("BRIDGE-ASSIGNED"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode multipleSuperIdenticalSuffixCopiesConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        appendMultipleSuperSuffix(method, owner);
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        appendMultipleSuperSuffix(method, owner);
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private void appendMultipleSuperSuffix(
            MethodNode method, String owner) {
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "original", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private MethodNode multipleSuperDiamondWithAstoreZero(
            String owner, String superName) {
        MethodNode method =
                multipleSuperDiamondConstructor(owner, superName);
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prefix.add(new VarInsnNode(Opcodes.ASTORE, 2));
        prefix.add(new InsnNode(Opcodes.ACONST_NULL));
        prefix.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.insert(prefix);
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL) {
                continue;
            }
            MethodInsnNode invoke = (MethodInsnNode) instruction;
            if (!"<init>".equals(invoke.name)
                    || !superName.equals(invoke.owner)) {
                continue;
            }
            for (AbstractInsnNode receiver = instruction.getPrevious();
                 receiver != null; receiver = receiver.getPrevious()) {
                if (receiver instanceof VarInsnNode
                        && receiver.getOpcode() == Opcodes.ALOAD
                        && ((VarInsnNode) receiver).var == 0) {
                    ((VarInsnNode) receiver).var = 2;
                    break;
                }
            }
        }
        method.maxLocals = 3;
        return method;
    }

    private MethodNode multipleSuperDiamondWithSkipPath(
            String owner, String superName) {
        MethodNode method =
                multipleSuperDiamondConstructor(owner, superName);
        JumpInsnNode admittedGoto = Arrays.stream(
                        method.instructions.toArray())
                .filter(JumpInsnNode.class::isInstance)
                .map(JumpInsnNode.class::cast)
                .filter(jump -> jump.getOpcode() == Opcodes.GOTO)
                .findFirst().orElseThrow(AssertionError::new);
        InsnList prefix = new InsnList();
        prefix.add(new VarInsnNode(Opcodes.ILOAD, 1));
        prefix.add(new JumpInsnNode(
                Opcodes.IFEQ, admittedGoto.label));
        method.instructions.insert(prefix);
        return method;
    }

    private MethodNode multipleSuperDiamondWithCrossingTryCatch(
            String owner, String superName) {
        MethodNode method =
                multipleSuperDiamondConstructor(owner, superName);
        MethodInsnNode firstChain = Arrays.stream(
                        method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(invoke.name)
                        && superName.equals(invoke.owner))
                .findFirst().orElseThrow(AssertionError::new);
        AbstractInsnNode suffixReturn =
                method.instructions.getLast();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.insertBefore(firstChain, start);
        method.instructions.insertBefore(suffixReturn, end);
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));
        return method;
    }

    private MethodNode multipleSuperDifferentSuffixConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode twoDistinctSuffixesWithProvenPrefixExtraConstructor(
            String owner, String superName) {
        MethodNode method =
                multipleSuperDifferentSuffixConstructor(owner, superName);
        java.util.List<MethodInsnNode> calls = Arrays.stream(
                        method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(invoke.name)
                        && superName.equals(invoke.owner))
                .collect(Collectors.toList());
        InsnList assigned = new InsnList();
        assigned.add(new VarInsnNode(Opcodes.ILOAD, 1));
        assigned.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.insert(assigned);
        for (MethodInsnNode call : calls) {
            InsnList read = new InsnList();
            read.add(new VarInsnNode(Opcodes.ILOAD, 2));
            read.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(call, read);
        }
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode twoDistinctSuffixesWithUnassignedBridgeExtraConstructor(
            String owner, String superName) {
        MethodNode method =
                multipleSuperDifferentSuffixConstructor(owner, superName);
        java.util.List<MethodInsnNode> calls = Arrays.stream(
                        method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(invoke.name)
                        && superName.equals(invoke.owner))
                .collect(Collectors.toList());
        InsnList assigned = new InsnList();
        assigned.add(new VarInsnNode(Opcodes.ILOAD, 1));
        assigned.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.insertBefore(
                calls.get(0).getPrevious().getPrevious(), assigned);
        for (MethodInsnNode call : calls) {
            InsnList read = new InsnList();
            read.add(new VarInsnNode(Opcodes.ILOAD, 2));
            read.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(call, read);
        }
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode twoDistinctSuffixesWithFieldExtraConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode rejectedTwoDifferentSuffixConstructor(
            String owner, String superName, String shape) {
        MethodNode method =
                multipleSuperDifferentSuffixConstructor(owner, superName);
        java.util.List<MethodInsnNode> calls = Arrays.stream(
                        method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(invoke.name)
                        && superName.equals(invoke.owner))
                .collect(Collectors.toList());
        if ("branch".equals(shape)) {
            AbstractInsnNode firstReturn = calls.get(0).getNext()
                    .getNext().getNext().getNext();
            LabelNode target = new LabelNode();
            method.instructions.insertBefore(
                    firstReturn, new JumpInsnNode(Opcodes.GOTO, target));
            method.instructions.insertBefore(firstReturn, target);
        } else if ("exception-table".equals(shape)) {
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            method.instructions.insert(calls.get(0), start);
            method.instructions.insertBefore(
                    calls.get(0).getNext().getNext().getNext().getNext(), end);
            method.instructions.add(handler);
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
            method.tryCatchBlocks.add(new TryCatchBlockNode(
                    start, end, handler, "java/lang/Throwable"));
        } else {
            throw new IllegalArgumentException("Unknown shape " + shape);
        }
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperSeparateReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode multipleSuperThreeDeclaredArgumentReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(IIII)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 5;
        method.maxStack = 2;
        return method;
    }

    private MethodNode multipleSuperThreeSeparateReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperThreeIsubImulReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperThreeBitwiseReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 7));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 16));
        method.instructions.add(new InsnNode(Opcodes.IOR));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperThreeShiftReturnsConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 34));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 34));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 34));
        method.instructions.add(new InsnNode(Opcodes.ISHR));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperThreeIdenticalSuffixCopiesConstructor(
            String superName) {
        return multipleSuperThreeSuffixCopiesConstructor(
                superName, Opcodes.ICONST_4,
                Opcodes.ICONST_4, Opcodes.ICONST_4);
    }

    private MethodNode multipleSuperThreeDistinctSuffixConstructor(
            String owner, String superName) {
        MethodNode method =
                multipleSuperThreeSeparateReturnsConstructor(superName);
        int[] constants = {
                Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5
        };
        int call = 0;
        for (AbstractInsnNode instruction :
                method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL
                    || !"<init>".equals(((MethodInsnNode) instruction).name)
                    || !superName.equals(
                    ((MethodInsnNode) instruction).owner)) {
                continue;
            }
            InsnList suffix = new InsnList();
            suffix.add(new VarInsnNode(Opcodes.ALOAD, 0));
            suffix.add(new InsnNode(constants[call++]));
            suffix.add(new FieldInsnNode(
                    Opcodes.PUTFIELD, owner, "result", "I"));
            method.instructions.insert(instruction, suffix);
        }
        if (call != constants.length) {
            throw new AssertionError("Expected three chain calls");
        }
        return method;
    }

    private MethodNode multipleSuperFourDistinctSuffixConstructor(
            String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode one = new LabelNode();
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, one));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(one);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.ICONST_3));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.ICONST_4));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.ICONST_5));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode multipleSuperThreeSuffixCopiesConstructor(
            String superName, int firstConstant,
            int secondConstant, int thirdConstant) {
        MethodNode method =
                multipleSuperThreeSeparateReturnsConstructor(superName);
        int[] constants = {firstConstant, secondConstant, thirdConstant};
        int call = 0;
        for (AbstractInsnNode instruction :
                method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)
                    || instruction.getOpcode() != Opcodes.INVOKESPECIAL
                    || !"<init>".equals(((MethodInsnNode) instruction).name)
                    || !superName.equals(
                    ((MethodInsnNode) instruction).owner)) {
                continue;
            }
            InsnList suffix = new InsnList();
            suffix.add(new InsnNode(constants[call++]));
            suffix.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(instruction, suffix);
        }
        if (call != constants.length) {
            throw new AssertionError("Expected three chain calls");
        }
        return method;
    }

    private MethodNode rejectedThreeNonemptySuffixCopiesConstructor(
            String superName, String shape) {
        MethodNode method = "partly-identical".equals(shape)
                ? multipleSuperThreeSuffixCopiesConstructor(
                superName, Opcodes.ICONST_4,
                Opcodes.ICONST_5, Opcodes.ICONST_4)
                : multipleSuperThreeSuffixCopiesConstructor(
                superName, Opcodes.ICONST_3,
                Opcodes.ICONST_4, Opcodes.ICONST_5);
        java.util.List<MethodInsnNode> calls = Arrays.stream(
                        method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .filter(invoke -> invoke.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(invoke.name)
                        && superName.equals(invoke.owner))
                .collect(Collectors.toList());

        if ("partly-identical".equals(shape)) {
            return method;
        } else if ("branch".equals(shape)) {
            AbstractInsnNode firstReturn =
                    calls.get(0).getNext().getNext().getNext();
            LabelNode target = new LabelNode();
            method.instructions.insertBefore(
                    firstReturn, new JumpInsnNode(Opcodes.GOTO, target));
            method.instructions.insertBefore(firstReturn, target);
        } else if ("skip-super".equals(shape)) {
            LabelNode continueToCalls = new LabelNode();
            InsnList prefix = new InsnList();
            prefix.add(new VarInsnNode(Opcodes.ILOAD, 1));
            prefix.add(new IntInsnNode(Opcodes.BIPUSH, 99));
            prefix.add(new JumpInsnNode(
                    Opcodes.IF_ICMPNE, continueToCalls));
            prefix.add(new InsnNode(Opcodes.RETURN));
            prefix.add(continueToCalls);
            method.instructions.insert(prefix);
        } else if ("exception-table".equals(shape)) {
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            LabelNode continueToCalls = new LabelNode();
            InsnList prefix = new InsnList();
            prefix.add(start);
            prefix.add(new VarInsnNode(Opcodes.ILOAD, 1));
            prefix.add(new InsnNode(Opcodes.POP));
            prefix.add(end);
            prefix.add(new JumpInsnNode(
                    Opcodes.GOTO, continueToCalls));
            prefix.add(handler);
            prefix.add(new InsnNode(Opcodes.ATHROW));
            prefix.add(continueToCalls);
            method.instructions.insert(prefix);
            method.tryCatchBlocks.add(new TryCatchBlockNode(
                    start, end, handler, "java/lang/Throwable"));
        } else if ("extra-local-suffix".equals(shape)) {
            InsnList prefix = new InsnList();
            prefix.add(new VarInsnNode(Opcodes.ILOAD, 1));
            prefix.add(new VarInsnNode(Opcodes.ISTORE, 2));
            method.instructions.insert(prefix);
            for (MethodInsnNode call : calls) {
                InsnList extraRead = new InsnList();
                extraRead.add(new VarInsnNode(Opcodes.ILOAD, 2));
                extraRead.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(call, extraRead);
            }
            method.maxLocals = 3;
        } else {
            throw new IllegalArgumentException("Unknown shape " + shape);
        }
        method.maxStack = 3;
        return method;
    }

    private MethodNode rejectedThreeImmediateReturnsConstructor(
            String superName, String shape) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode negative = new LabelNode();
        LabelNode zero = new LabelNode();
        boolean extraLocalOperand = "extra-local".equals(shape)
                || "iadd-extra-local".equals(shape)
                || "isub-extra-local".equals(shape)
                || "imul-extra-local".equals(shape)
                || "iand-extra-local".equals(shape)
                || "ior-extra-local".equals(shape)
                || "ixor-extra-local".equals(shape)
                || "ishl-extra-local".equals(shape)
                || "ishr-extra-local".equals(shape)
                || "iushr-extra-local".equals(shape);

        if (extraLocalOperand) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        } else if ("astore-zero".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        } else if ("skip-super".equals(shape)) {
            LabelNode continueToCalls = new LabelNode();
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 99));
            method.instructions.add(new JumpInsnNode(
                    Opcodes.IF_ICMPNE, continueToCalls));
            method.instructions.add(new InsnNode(Opcodes.RETURN));
            method.instructions.add(continueToCalls);
        } else if ("exception-table".equals(shape)) {
            LabelNode start = new LabelNode();
            LabelNode end = new LabelNode();
            LabelNode handler = new LabelNode();
            LabelNode continueToCalls = new LabelNode();
            method.instructions.add(start);
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
            method.instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.add(end);
            method.instructions.add(new JumpInsnNode(
                    Opcodes.GOTO, continueToCalls));
            method.instructions.add(handler);
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
            method.instructions.add(continueToCalls);
            method.tryCatchBlocks.add(new TryCatchBlockNode(
                    start, end, handler, "java/lang/Throwable"));
        } else if (!"nested-iadd".equals(shape)
                && !"nested-isub".equals(shape)
                && !"nested-imul".equals(shape)
                && !"nested-iand".equals(shape)
                && !"nested-ior".equals(shape)
                && !"nested-ixor".equals(shape)
                && !"nested-ishl".equals(shape)
                && !"nested-ishr".equals(shape)
                && !"nested-iushr".equals(shape)
                && !"idiv".equals(shape)
                && !"post-call".equals(shape)) {
            throw new IllegalArgumentException("Unknown shape " + shape);
        }

        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, zero));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLT, negative));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(
                Opcodes.ILOAD,
                extraLocalOperand ? 2 : 1));
        if ("iadd-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IADD));
        } else if ("isub-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.ISUB));
        } else if ("imul-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IMUL));
        } else if ("iand-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IAND));
        } else if ("ior-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IOR));
        } else if ("ixor-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IXOR));
        } else if ("ishl-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.ISHL));
        } else if ("ishr-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.ISHR));
        } else if ("iushr-extra-local".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IUSHR));
        } else if ("nested-iadd".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IADD));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IADD));
        } else if ("nested-isub".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.ISUB));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.ISUB));
        } else if ("nested-imul".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IMUL));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IMUL));
        } else if ("nested-iand".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IAND));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IAND));
        } else if ("nested-ior".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IOR));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IOR));
        } else if ("nested-ixor".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IXOR));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IXOR));
        } else if ("nested-ishl".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IADD));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.ISHL));
        } else if ("nested-ishr".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IADD));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.ISHR));
        } else if ("nested-iushr".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_1));
            method.instructions.add(new InsnNode(Opcodes.IADD));
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IUSHR));
        } else if ("idiv".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_2));
            method.instructions.add(new InsnNode(Opcodes.IDIV));
        }
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        if ("post-call".equals(shape)) {
            method.instructions.add(new InsnNode(Opcodes.ICONST_0));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(negative);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(zero);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = extraLocalOperand ? 3 : 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode multipleSuperMain(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendMultipleSuperPrint(
                method, owner, 7, superName, "magnitude");
        appendMultipleSuperPrint(
                method, owner, -5, superName, "magnitude");
        appendMultipleSuperPrint(
                method, owner, -5, owner, "original");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode twoDifferentSuffixMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendMultipleSuperPrint(
                method, owner, 7, owner, "result");
        appendMultipleSuperPrint(
                method, owner, -5, owner, "result");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode threeDistinctSuffixMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendMultipleSuperPrint(
                method, owner, 7, owner, "result");
        appendMultipleSuperPrint(
                method, owner, -5, owner, "result");
        appendMultipleSuperPrint(
                method, owner, 0, owner, "result");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode postChainConditionalBranchMain(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendPostChainConditionalBranchPrint(
                method, owner, 7, 1, superName, "magnitude");
        appendPostChainConditionalBranchPrint(
                method, owner, 7, 1, owner, "result");
        appendPostChainConditionalBranchPrint(
                method, owner, 7, 0, superName, "magnitude");
        appendPostChainConditionalBranchPrint(
                method, owner, 7, 0, owner, "result");
        appendPostChainConditionalBranchPrint(
                method, owner, -5, 0, superName, "magnitude");
        appendPostChainConditionalBranchPrint(
                method, owner, -5, 0, owner, "result");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 5;
        return method;
    }

    private MethodNode postChainSwitchMain(
            String tableOwner, String lookupOwner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendPostChainSwitchPrint(method, tableOwner, 7, 0);
        appendPostChainSwitchPrint(method, tableOwner, 7, 1);
        appendPostChainSwitchPrint(method, tableOwner, 7, 99);
        appendPostChainSwitchPrint(method, tableOwner, -5, 99);
        appendPostChainSwitchPrint(method, lookupOwner, 7, 7);
        appendPostChainSwitchPrint(method, lookupOwner, 7, 42);
        appendPostChainSwitchPrint(method, lookupOwner, 7, 99);
        appendPostChainSwitchPrint(method, lookupOwner, -5, 99);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 5;
        return method;
    }

    private void appendPostChainSwitchPrint(
            MethodNode method, String owner, int selector, int key) {
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, selector));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, key));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", "(II)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
    }

    private MethodNode postChainIntCompareFamilyMain(String ownerPrefix) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        for (int opcode : POST_CHAIN_INT_COMPARE_OPCODES) {
            int[][] operands = postChainCompareOperands(opcode);
            String owner = ownerPrefix + opcode;
            appendPostChainIntComparePrint(
                    method, owner, opcode, 7, operands[0]);
            appendPostChainIntComparePrint(
                    method, owner, opcode, 7, operands[1]);
            appendPostChainIntComparePrint(
                    method, owner, opcode, -5, operands[1]);
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 6;
        return method;
    }

    private void appendPostChainIntComparePrint(
            MethodNode method, String owner, int opcode,
            int selector, int[] operands) {
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, selector));
        for (int operand : operands) {
            method.instructions.add(
                    new IntInsnNode(Opcodes.BIPUSH, operand));
        }
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>",
                isBinaryIntCompare(opcode) ? "(III)V" : "(II)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
    }

    private MethodNode conditionalBridgeExtraMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(Ljava/lang/String;)V", false));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new LdcInsnNode("PREFIX-EXIT"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private void appendPostChainConditionalBranchPrint(
            MethodNode method, String owner, int value, int branch,
            String fieldOwner, String fieldName) {
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        method.instructions.add(new InsnNode(
                branch == 0 ? Opcodes.ICONST_0 : Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", "(II)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, fieldOwner, fieldName, "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
    }

    private boolean isBinaryIntCompare(int opcode) {
        return opcode >= Opcodes.IF_ICMPEQ
                && opcode <= Opcodes.IF_ICMPLE;
    }

    private int[][] postChainCompareOperands(int opcode) {
        switch (opcode) {
            case Opcodes.IFEQ:
                return new int[][]{{0}, {1}};
            case Opcodes.IFNE:
                return new int[][]{{1}, {0}};
            case Opcodes.IFLT:
                return new int[][]{{-1}, {0}};
            case Opcodes.IFGE:
                return new int[][]{{0}, {-1}};
            case Opcodes.IFGT:
                return new int[][]{{1}, {0}};
            case Opcodes.IFLE:
                return new int[][]{{0}, {1}};
            case Opcodes.IF_ICMPEQ:
                return new int[][]{{2, 2}, {2, 3}};
            case Opcodes.IF_ICMPNE:
                return new int[][]{{2, 3}, {2, 2}};
            case Opcodes.IF_ICMPLT:
                return new int[][]{{2, 3}, {3, 2}};
            case Opcodes.IF_ICMPGE:
                return new int[][]{{3, 2}, {2, 3}};
            case Opcodes.IF_ICMPGT:
                return new int[][]{{3, 2}, {2, 3}};
            case Opcodes.IF_ICMPLE:
                return new int[][]{{2, 3}, {3, 2}};
            default:
                throw new IllegalArgumentException(
                        "Not an int compare opcode: " + opcode);
        }
    }

    private Object newPostChainCompareInstance(
            Class<?> owner, int opcode, int selector, int[] operands)
            throws Exception {
        if (isBinaryIntCompare(opcode)) {
            return owner.getConstructor(
                            int.class, int.class, int.class)
                    .newInstance(selector, operands[0], operands[1]);
        }
        return owner.getConstructor(int.class, int.class)
                .newInstance(selector, operands[0]);
    }

    private MethodNode multipleSuperSeparateReturnsMain(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendMultipleSuperPrint(
                method, owner, 7, superName, "magnitude");
        appendMultipleSuperPrint(
                method, owner, -5, superName, "magnitude");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode multipleSuperThreeSeparateReturnsMain(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendMultipleSuperPrint(
                method, owner, 11, superName, "magnitude");
        appendMultipleSuperPrint(
                method, owner, -22, superName, "magnitude");
        appendMultipleSuperPrint(
                method, owner, 0, superName, "magnitude");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private void appendMultipleSuperPrint(
            MethodNode method, String owner, int value,
            String fieldOwner, String fieldName) {
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner, "<init>", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, fieldOwner, fieldName, "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
    }

    private MethodNode prefixLocalBranchConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode chain = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/Math", "abs", "(I)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, chain));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW,
                "java/lang/IllegalArgumentException"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("zero"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/IllegalArgumentException", "<init>",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "example/Base", "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode prefixOnlyTryCatchConstructor(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/String;)V", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode chain = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, "java/lang/Integer",
                "parseInt", "(Ljava/lang/String;)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(end);
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, superName,
                "<init>", "(I)V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/NumberFormatException"));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode suffixTryCatchWithPrefixReturnHandler(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode handler = new LabelNode();
        LabelNode chain = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(start);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 24));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode suffixTryCatchWithPrefixGotoReturnHandler(String owner) {
        return suffixTryCatchWithPrefixGotoReturnHandler(owner, null);
    }

    private MethodNode invalidPrefixGotoReturnHandler(
            String owner, String shape) {
        return suffixTryCatchWithPrefixGotoReturnHandler(owner, shape);
    }

    private MethodNode suffixTryCatchWithPrefixGotoReturnHandler(
            String owner, String invalidShape) {
        if (invalidShape != null
                && !"extra-work".equals(invalidShape)
                && !"non-return-target".equals(invalidShape)
                && !"extra-return-incoming".equals(invalidShape)) {
            throw new IllegalArgumentException(invalidShape);
        }
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode handler = new LabelNode();
        LabelNode handlerReturn = new LabelNode();
        LabelNode chain = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        if ("extra-return-incoming".equals(invalidShape)) {
            method.instructions.add(
                    new JumpInsnNode(Opcodes.GOTO, handlerReturn));
            method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        }
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        if ("extra-work".equals(invalidShape)) {
            method.instructions.add(new InsnNode(Opcodes.NOP));
        }
        method.instructions.add(
                new JumpInsnNode(Opcodes.GOTO, handlerReturn));
        method.instructions.add(handlerReturn);
        if ("non-return-target".equals(invalidShape)) {
            method.instructions.add(new InsnNode(Opcodes.NOP));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(start);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 24));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode suffixTryCatchWithPrefixAstoreReturnHandler(
            String owner) {
        return suffixTryCatchWithPrefixAstoreReturnHandler(
                owner, false, null);
    }

    private MethodNode suffixTryCatchWithPrefixAstoreGotoReturnHandler(
            String owner) {
        return suffixTryCatchWithPrefixAstoreReturnHandler(
                owner, true, null);
    }

    private MethodNode invalidPrefixAstoreReturnHandler(
            String owner, String shape) {
        boolean gotoReturn = "non-return-target".equals(shape)
                || "extra-return-incoming".equals(shape);
        return suffixTryCatchWithPrefixAstoreReturnHandler(
                owner, gotoReturn, shape);
    }

    private MethodNode suffixTryCatchWithPrefixAstoreReturnHandler(
            String owner, boolean gotoReturn, String invalidShape) {
        if (invalidShape != null
                && !"astore-0".equals(invalidShape)
                && !"extra-work".equals(invalidShape)
                && !"stored-exception-use".equals(invalidShape)
                && !"non-return-target".equals(invalidShape)
                && !"extra-return-incoming".equals(invalidShape)
                && !"category-2-hole".equals(invalidShape)) {
            throw new IllegalArgumentException(invalidShape);
        }
        String descriptor = "category-2-hole".equals(invalidShape)
                ? "(IJ)V" : "(I)V";
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", descriptor, null, null);
        LabelNode handler = new LabelNode();
        LabelNode handlerReturn = new LabelNode();
        LabelNode chain = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        if ("extra-return-incoming".equals(invalidShape)) {
            method.instructions.add(
                    new JumpInsnNode(Opcodes.GOTO, handlerReturn));
            method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        }
        method.instructions.add(handler);
        int handlerLocal = "astore-0".equals(invalidShape) ? 0 : 3;
        method.instructions.add(
                new VarInsnNode(Opcodes.ASTORE, handlerLocal));
        if ("extra-work".equals(invalidShape)) {
            method.instructions.add(new InsnNode(Opcodes.NOP));
        } else if ("stored-exception-use".equals(invalidShape)) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            method.instructions.add(new InsnNode(Opcodes.POP));
        }
        if (gotoReturn) {
            method.instructions.add(
                    new JumpInsnNode(Opcodes.GOTO, handlerReturn));
            method.instructions.add(handlerReturn);
            if ("non-return-target".equals(invalidShape)) {
                method.instructions.add(new InsnNode(Opcodes.NOP));
            }
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(start);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 24));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode suffixTryCatchWithPrefixAthrowHandler(String owner) {
        return suffixTryCatchWithPrefixAthrowHandler(
                owner, false, null);
    }

    private MethodNode suffixTryCatchWithPrefixAstoreAthrowHandler(
            String owner) {
        return suffixTryCatchWithPrefixAthrowHandler(
                owner, true, null);
    }

    private MethodNode invalidPrefixAthrowHandler(
            String owner, String shape) {
        return suffixTryCatchWithPrefixAthrowHandler(
                owner, false, shape);
    }

    private MethodNode suffixTryCatchWithPrefixAthrowHandler(
            String owner, boolean storeAndReload, String invalidShape) {
        if (invalidShape != null
                && !"astore-0".equals(invalidShape)
                && !"pop-athrow".equals(invalidShape)
                && !"extra-work".equals(invalidShape)
                && !"astore-without-reload".equals(invalidShape)
                && !"stored-exception-use".equals(invalidShape)) {
            throw new IllegalArgumentException(invalidShape);
        }
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode handler = new LabelNode();
        LabelNode chain = new LabelNode();
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, chain));
        method.instructions.add(handler);
        if (invalidShape == null) {
            method.instructions.add(new FrameNode(
                    Opcodes.F_SAME1, 0, null, 1,
                    new Object[]{"java/lang/ArithmeticException"}));
        }
        if ("astore-0".equals(invalidShape)) {
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else if ("pop-athrow".equals(invalidShape)) {
            method.instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else if ("extra-work".equals(invalidShape)) {
            method.instructions.add(new InsnNode(Opcodes.NOP));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else if ("astore-without-reload".equals(invalidShape)) {
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else if ("stored-exception-use".equals(invalidShape)) {
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            method.instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else if (storeAndReload) {
            method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        } else {
            method.instructions.add(new InsnNode(Opcodes.ATHROW));
        }
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(start);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 24));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode suffixOnlyTryCatchConstructor(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        method.instructions.add(start);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 24));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(end);
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTFIELD, owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/ArithmeticException"));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode mixedTryCatchLabelsConstructor(int prefixMask) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        LabelNode[] prefix = {
                new LabelNode(), new LabelNode(), new LabelNode()
        };
        LabelNode[] suffix = {
                new LabelNode(), new LabelNode(), new LabelNode()
        };
        for (LabelNode label : prefix) {
            method.instructions.add(label);
        }
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, "java/lang/Object",
                "<init>", "()V", false));
        for (LabelNode label : suffix) {
            method.instructions.add(label);
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                (prefixMask & 1) != 0 ? prefix[0] : suffix[0],
                (prefixMask & 2) != 0 ? prefix[1] : suffix[1],
                (prefixMask & 4) != 0 ? prefix[2] : suffix[2],
                "java/lang/Throwable"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode prefixBranchTargetingSuffixConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode suffix = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNE, suffix));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(suffix);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode suffixJumpIntoPrefixConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        LabelNode prefix = new LabelNode();
        method.instructions.add(prefix);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, prefix));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode tryCatchCrossingConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/Throwable"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode multipleSuperCallConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private boolean retainsBridgeInvocation(MethodNode method, String bridgeName) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESTATIC
                    && bridgeName.equals(((MethodInsnNode) instruction).name)) {
                return true;
            }
        }
        return false;
    }

    private int directChainCallCount(
            MethodNode method, ClassNode owner) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                if ("<init>".equals(invoke.name)
                        && (owner.name.equals(invoke.owner)
                        || owner.superName.equals(invoke.owner))) {
                    count++;
                }
            }
        }
        return count;
    }

    private int hiddenBridgeCallCount(MethodNode method) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESTATIC
                    && ((MethodInsnNode) instruction).owner.contains("/hidden/")) {
                count++;
            }
        }
        return count;
    }

    private MethodInsnNode retainedThisOrSuperCall(MethodNode method, ClassNode owner) {
        for (org.objectweb.asm.tree.AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode
                    && instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode invoke = (MethodInsnNode) instruction;
                if ("<init>".equals(invoke.name)
                        && (owner.name.equals(invoke.owner)
                        || owner.superName.equals(invoke.owner))) {
                    return invoke;
                }
            }
        }
        throw new AssertionError("no retained this/super call");
    }

    private MethodNode unsupportedConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_1));
        method.instructions.add(new InsnNode(Opcodes.FADD));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode prefixExtraReferenceConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/String;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "toUpperCase",
                "()Ljava/lang/String;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode gappedPrefixExtraReferenceConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/String;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "toUpperCase",
                "()Ljava/lang/String;", false));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode gappedPrefixExtraIincConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new IincInsnNode(3, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode prefixExtraIntConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "I"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode prefixExtraPrimitiveConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 17));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new LdcInsnNode(23L));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 2));
        method.instructions.add(new LdcInsnNode(3.5f));
        method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 4));
        method.instructions.add(new LdcInsnNode(7.25d));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 5));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "intValue", "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "longValue", "J"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "floatValue", "F"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 5));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "doubleValue", "D"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 7;
        method.maxStack = 3;
        return method;
    }

    private MethodNode conditionallyAssignedPrefixExtraConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(I)V", null, null);
        LabelNode chain = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, chain));
        method.instructions.add(new LdcInsnNode("assigned"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(chain);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "Ljava/lang/Object;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode prefixExtraReferenceMain(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "java/lang/System", "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("prefix-extra-forwarded"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                owner, "<init>", "(Ljava/lang/String;)V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode prefixOnlyTryCatchMain(
            String owner, String superName) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        appendPrefixOnlyTryCatchPrint(
                method, owner, "37", superName, "magnitude");
        appendPrefixOnlyTryCatchPrint(
                method, owner, "37", owner, "result");
        appendPrefixOnlyTryCatchPrint(
                method, owner, "not-an-integer", superName, "magnitude");
        appendPrefixOnlyTryCatchPrint(
                method, owner, "not-an-integer", owner, "result");
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private void appendPrefixOnlyTryCatchPrint(
            MethodNode method, String owner, String value,
            String fieldOwner, String fieldName) {
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode(value));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner,
                "<init>", "(Ljava/lang/String;)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, fieldOwner, fieldName, "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
    }

    private MethodNode relocatedPrefixReturnHandlerMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner,
                "<init>", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner,
                "<init>", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode relocatedPrefixAthrowHandlerMain(String owner) {
        MethodNode method = new MethodNode(
                Opcodes.ASM9, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner,
                "<init>", "(I)V", false));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD, owner, "result", "I"));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));

        LabelNode caughtStart = new LabelNode();
        LabelNode caughtEnd = new LabelNode();
        LabelNode caughtHandler = new LabelNode();
        LabelNode done = new LabelNode();
        method.instructions.add(caughtStart);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL, owner,
                "<init>", "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(caughtEnd);
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, done));
        method.instructions.add(caughtHandler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, "java/lang/System",
                "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/io/PrintStream",
                "println", "(I)V", false));
        method.instructions.add(done);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                caughtStart, caughtEnd, caughtHandler,
                "java/lang/ArithmeticException"));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode referenceParameterReassignedBeforeSuperConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/String;Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 1;
        return method;
    }

    private MethodNode referenceParameterAstoreResultConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/String;Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "toString", "()Ljava/lang/String;", false));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode referenceParameterAstoreMain(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "java/lang/System", "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("discarded"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW,
                "java/lang/StringBuilder"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new LdcInsnNode("forwarded-result"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/StringBuilder", "<init>",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                owner, "<init>",
                "(Ljava/lang/String;Ljava/lang/Object;)V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 6;
        return method;
    }

    private MethodNode receiverReassignedBeforeSuperConstructor() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "(Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 1;
        return method;
    }

    private MethodNode identityAstoreZeroConstructor(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "<init>", "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (owner.endsWith("Runtime")) {
            method.instructions.add(new LdcInsnNode("IDENTITY-ASTORE-0"));
            method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                    owner, "result", "Ljava/lang/String;"));
        } else {
            method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 37));
            method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                    owner, "result", "I"));
        }
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode identityAstoreZeroMain(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "main", "([Ljava/lang/String;)V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "java/lang/System", "out", "Ljava/io/PrintStream;"));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, owner));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                owner, "<init>", "()V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                owner, "result", "Ljava/lang/String;"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/io/PrintStream", "println",
                "(Ljava/lang/String;)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 3;
        return method;
    }

    private MethodNode returnAllocatedObjectMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "returnAllocatedObject", "()Ljava/lang/Object;", null, null);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode returnAllocatedObjectArrayMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "returnAllocatedObjectArray", "(I)[Ljava/lang/Object;", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode returnNullMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "returnNull", "()Ljava/lang/Object;", null, null);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode referenceNullBranchMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        LabelNode target = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(opcode, target));
        if (opcode == Opcodes.IFNULL) {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        } else {
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(target);
        if (opcode == Opcodes.IFNULL) {
            method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        } else {
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        }
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode referenceCompareBranchMethod(String name, int opcode) {
        // Returns 1 when the branch is taken, 0 on fall-through. For
        // IF_ACMPEQ the branch is taken on identity equality, so the method
        // returns 1 iff the two references are the same object (both null
        // included). For IF_ACMPNE it returns 1 iff they differ.
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(Ljava/lang/Object;Ljava/lang/Object;)I", null, null);
        LabelNode taken = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new JumpInsnNode(opcode, taken));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(taken);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode popUnusedCategoryOneInvokeResultMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "discardInvokeResult", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "identity", "(I)I", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode popLongMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "popLong", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedAfterPhaseNineOpsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseNineOps",
                "(Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        LabelNode nonNull = new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, nonNull));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(nonNull);
        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedAfterPhaseTenFieldsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseTenFields",
                "(Lexample/Math;JLjava/lang/Object;[I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "longValue", "J"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "longValue", "J"));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "objectValue", "Ljava/lang/Object;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "objectValue", "Ljava/lang/Object;"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 3));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 1));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", "staticLongValue", "J"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "staticLongValue", "J"));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", "staticArrayValue", "[I"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "staticArrayValue", "[I"));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "unsupportedFloat", "F"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 5;
        method.maxStack = 3;
        return method;
    }

    private MethodNode unsupportedAfterPhaseElevenInvokesMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "unsupportedAfterPhaseElevenInvokes",
                "(Lexample/Phase11Interface;I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "example/Phase11Interface", "adjust", "(I)I", true));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "example/Base", "superInt", "(I)I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedAfterPhaseFourteenOpsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseFourteenOps",
                "(Lexample/Math;Lexample/PrimitiveTarget;IIII)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "booleanValue", "Z"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "booleanValue", "Z"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", "byteValue", "B"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "byteValue", "B"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/PrimitiveTarget", "identityChar", "(C)C", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 5));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "example/PrimitiveTarget", "identityShort", "(S)S", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "example/Phase11Interface", "identityBoolean", "(Z)Z", true));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "example/Base", "identityByte", "(B)B", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_1));
        method.instructions.add(new InsnNode(Opcodes.FADD));
        method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 6));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 6));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "floatValue", "F"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "floatValue", "F"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/PrimitiveTarget", "identityFloat", "(F)F", false));
        method.instructions.add(new VarInsnNode(Opcodes.FSTORE, 6));
        method.instructions.add(new LdcInsnNode(Double.longBitsToDouble(
                0x7ff8000000001234L)));
        method.instructions.add(new InsnNode(Opcodes.DCONST_1));
        method.instructions.add(new InsnNode(Opcodes.DREM));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 7));
        method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 7));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                "example/Math", "doubleValue", "D"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "doubleValue", "D"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/PrimitiveTarget", "identityDouble", "(D)D", false));
        method.instructions.add(new VarInsnNode(Opcodes.DSTORE, 7));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 9;
        method.maxStack = 4;
        return method;
    }

    private MethodNode staticLongInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "callLong", "(J)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "identityLong", "(J)J", false));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode virtualStringInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "virtualStringLength", "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "toString", "()Ljava/lang/String;", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode staticStringInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "staticStringLength", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/String", "valueOf", "(I)Ljava/lang/String;", false));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode staticVoidLongInvokeMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "consumeLong", "(J)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "example/Math", "acceptLong", "(J)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedAfterNewMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterNew", "()V", null, null);
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode stringLengthMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "stringLength", "(Ljava/lang/String;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode floatingScalarOpsMethod(boolean wide) {
        Type type = wide ? Type.DOUBLE_TYPE : Type.FLOAT_TYPE;
        int load = wide ? Opcodes.DLOAD : Opcodes.FLOAD;
        int store = wide ? Opcodes.DSTORE : Opcodes.FSTORE;
        int add = wide ? Opcodes.DADD : Opcodes.FADD;
        int sub = wide ? Opcodes.DSUB : Opcodes.FSUB;
        int multiply = wide ? Opcodes.DMUL : Opcodes.FMUL;
        int divide = wide ? Opcodes.DDIV : Opcodes.FDIV;
        int remainder = wide ? Opcodes.DREM : Opcodes.FREM;
        int negate = wide ? Opcodes.DNEG : Opcodes.FNEG;
        int cmpl = wide ? Opcodes.DCMPL : Opcodes.FCMPL;
        int cmpg = wide ? Opcodes.DCMPG : Opcodes.FCMPG;
        int rightLocal = type.getSize();
        int temporaryLocal = rightLocal + type.getSize();
        int comparisonLocal = temporaryLocal + type.getSize();
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                wide ? "doubleScalarOps" : "floatScalarOps",
                Type.getMethodDescriptor(Type.INT_TYPE, type, type), null, null);

        method.instructions.add(new VarInsnNode(load, 0));
        method.instructions.add(new VarInsnNode(load, rightLocal));
        method.instructions.add(new InsnNode(add));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new InsnNode(wide ? Opcodes.DCONST_0 : Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(sub));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new InsnNode(wide ? Opcodes.DCONST_1 : Opcodes.FCONST_1));
        method.instructions.add(new InsnNode(multiply));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        if (wide) {
            method.instructions.add(new VarInsnNode(load, rightLocal));
        } else {
            method.instructions.add(new InsnNode(Opcodes.FCONST_2));
        }
        method.instructions.add(new InsnNode(divide));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new VarInsnNode(load, rightLocal));
        method.instructions.add(new InsnNode(remainder));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new InsnNode(negate));
        method.instructions.add(new VarInsnNode(store, temporaryLocal));

        Number nan;
        if (wide) {
            nan = Double.longBitsToDouble(0x7ff8000000001234L);
        } else {
            nan = Float.intBitsToFloat(0x7fc01234);
        }
        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new LdcInsnNode(nan));
        method.instructions.add(new InsnNode(cmpl));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, comparisonLocal));
        method.instructions.add(new VarInsnNode(load, temporaryLocal));
        method.instructions.add(new LdcInsnNode(nan));
        method.instructions.add(new InsnNode(cmpg));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, comparisonLocal));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = comparisonLocal + 1;
        method.maxStack = type.getSize() * 2;
        return method;
    }

    private MethodNode floatingConversionMethod(int opcode) {
        Type operand;
        Type result;
        switch (opcode) {
            case Opcodes.I2F:
                operand = Type.INT_TYPE;
                result = Type.FLOAT_TYPE;
                break;
            case Opcodes.F2I:
                operand = Type.FLOAT_TYPE;
                result = Type.INT_TYPE;
                break;
            case Opcodes.L2F:
                operand = Type.LONG_TYPE;
                result = Type.FLOAT_TYPE;
                break;
            case Opcodes.F2L:
                operand = Type.FLOAT_TYPE;
                result = Type.LONG_TYPE;
                break;
            case Opcodes.I2D:
                operand = Type.INT_TYPE;
                result = Type.DOUBLE_TYPE;
                break;
            case Opcodes.D2I:
                operand = Type.DOUBLE_TYPE;
                result = Type.INT_TYPE;
                break;
            case Opcodes.L2D:
                operand = Type.LONG_TYPE;
                result = Type.DOUBLE_TYPE;
                break;
            case Opcodes.D2L:
                operand = Type.DOUBLE_TYPE;
                result = Type.LONG_TYPE;
                break;
            case Opcodes.F2D:
                operand = Type.FLOAT_TYPE;
                result = Type.DOUBLE_TYPE;
                break;
            case Opcodes.D2F:
                operand = Type.DOUBLE_TYPE;
                result = Type.FLOAT_TYPE;
                break;
            default:
                throw new IllegalArgumentException("Unsupported conversion opcode " + opcode);
        }
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "conversion" + opcode, Type.getMethodDescriptor(result, operand), null, null);
        method.instructions.add(new VarInsnNode(operand.getOpcode(Opcodes.ILOAD), 0));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = operand.getSize();
        method.maxStack = Math.max(operand.getSize(), result.getSize());
        return method;
    }

    private MethodNode floatingStackPhiMethod(boolean wide) {
        Type type = wide ? Type.DOUBLE_TYPE : Type.FLOAT_TYPE;
        int load = wide ? Opcodes.DLOAD : Opcodes.FLOAD;
        int returnOpcode = wide ? Opcodes.DRETURN : Opcodes.FRETURN;
        int conditionLocal = type.getSize();
        LabelNode alternate = new LabelNode();
        LabelNode join = new LabelNode();
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                wide ? "doublePhi" : "floatPhi",
                Type.getMethodDescriptor(type, type, Type.INT_TYPE), null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, conditionLocal));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, alternate));
        method.instructions.add(new VarInsnNode(load, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(alternate);
        method.instructions.add(new InsnNode(wide ? Opcodes.DCONST_1 : Opcodes.FCONST_1));
        method.instructions.add(join);
        method.instructions.add(new InsnNode(returnOpcode));
        method.maxLocals = conditionLocal + 1;
        method.maxStack = type.getSize();
        return method;
    }

    private MethodNode bitwiseShiftMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "mix", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IAND));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IOR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISHL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISHR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IUSHR));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unaryMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "narrow", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.INEG));
        method.instructions.add(new InsnNode(Opcodes.I2B));
        method.instructions.add(new InsnNode(Opcodes.I2S));
        method.instructions.add(new InsnNode(Opcodes.I2C));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode swapMethod(Type first, Type second) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "swap" + first.getSort() + second.getSort(),
                Type.getMethodDescriptor(second, first, second), null, null);
        method.instructions.add(new VarInsnNode(first.getOpcode(Opcodes.ILOAD), 0));
        method.instructions.add(new VarInsnNode(second.getOpcode(Opcodes.ILOAD),
                first.getSize()));
        method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(second.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = first.getSize() + second.getSize();
        method.maxStack = 2;
        return method;
    }

    private MethodNode invalidSwapMethod(Type first, Type second) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "invalidSwap" + first.getSort() + second.getSort(),
                Type.getMethodDescriptor(Type.VOID_TYPE, first, second), null, null);
        method.instructions.add(new VarInsnNode(first.getOpcode(Opcodes.ILOAD), 0));
        method.instructions.add(new VarInsnNode(second.getOpcode(Opcodes.ILOAD),
                first.getSize()));
        method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = first.getSize() + second.getSize();
        method.maxStack = first.getSize() + second.getSize();
        return method;
    }

    private MethodNode intArrayMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "bump", "([II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IALOAD));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IASTORE));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 4;
        return method;
    }

    private MethodNode referenceArrayRoundTripMethod(boolean stringArray) {
        String element = stringArray ? "Ljava/lang/String;" : "Ljava/lang/Object;";
        String array = "[" + element;
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                stringArray ? "stringArrayRoundTrip" : "objectArrayRoundTrip",
                "(" + array + "I" + element + ")" + element, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.AASTORE));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode nullReferenceArrayLoadCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchNullAaload", "()Ljava/lang/Object;", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NullPointerException"));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode referenceArrayBoundsCatchMethod(boolean upperBound) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                upperBound ? "catchUpperAaload" : "catchNegativeAaload",
                "([Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (upperBound) {
            method.instructions.add(new InsnNode(Opcodes.DUP));
            method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        } else {
            method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        }
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArrayIndexOutOfBoundsException"));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode wrongTypeReferenceArrayStoreCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchArrayStore",
                "([Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.AASTORE));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArrayStoreException"));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode unsupportedAfterPhaseSixteenOpsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseSixteenOps",
                "([Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.AASTORE));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.AALOAD));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 3;
        method.maxStack = 3;
        return method;
    }

    private MethodNode unsupportedAfterPhaseSeventeenStackOpsMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseSeventeenStackOps", "()V", null, null);

        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DUP2));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.POP2));

        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.DUP_X2));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.POP));

        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.SWAP));
        method.instructions.add(new InsnNode(Opcodes.DUP2_X1));
        for (int i = 0; i < 5; i++) {
            method.instructions.add(new InsnNode(Opcodes.POP));
        }

        method.instructions.add(new InsnNode(Opcodes.LCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DCONST_0));
        method.instructions.add(new InsnNode(Opcodes.DUP2_X2));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.POP2));

        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.FCONST_0));
        method.instructions.add(new InsnNode(Opcodes.POP2));

        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 6;
        return method;
    }

    private MethodNode newIntArrayCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "allocate", "(I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -12));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NegativeArraySizeException"));
        method.maxLocals = 3;
        method.maxStack = 1;
        return method;
    }

    private MethodNode primitiveArrayRoundTripMethod(
            String name, int atype, Type elementType) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, Type.getMethodDescriptor(elementType, Type.INT_TYPE, elementType),
                null, null);
        int arrayLocal = 1 + elementType.getSize();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, atype));
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, arrayLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, arrayLocal));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(elementType.getOpcode(Opcodes.ILOAD), 1));
        method.instructions.add(new InsnNode(elementType.getOpcode(Opcodes.IASTORE)));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, arrayLocal));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(elementType.getOpcode(Opcodes.IALOAD)));
        method.instructions.add(new InsnNode(elementType.getOpcode(Opcodes.IRETURN)));
        method.maxLocals = arrayLocal + 1;
        method.maxStack = 2 + elementType.getSize();
        return method;
    }

    private MethodNode newPrimitiveArrayCatchMethod(String name, int atype) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, atype));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -18));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NegativeArraySizeException"));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode multiArrayMethod(String name, String descriptor, int dimensions) {
        Type[] arguments = new Type[dimensions];
        Arrays.fill(arguments, Type.INT_TYPE);
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, Type.getMethodDescriptor(Type.getType(descriptor), arguments),
                null, null);
        for (int dimension = 0; dimension < dimensions; dimension++) {
            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, dimension));
        }
        method.instructions.add(new MultiANewArrayInsnNode(descriptor, dimensions));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = dimensions;
        method.maxStack = dimensions;
        return method;
    }

    private MethodNode multiArrayNegativeCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchNegativeMultiArray", "(II)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MultiANewArrayInsnNode("[[I", 2));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -19));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NegativeArraySizeException"));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode unsupportedAfterPhaseEighteenArraysMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseEighteenArrays", "()V", null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MultiANewArrayInsnNode("[[I", 2));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new MultiANewArrayInsnNode(
                "[[Ljava/lang/String;", 2));
        method.instructions.add(new InsnNode(Opcodes.POP));
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode tableSwitchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "tableSelect", "(II)I", null, null);
        LabelNode minusOne = new LabelNode();
        LabelNode zero = new LabelNode();
        LabelNode one = new LabelNode();
        LabelNode fallback = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TableSwitchInsnNode(-1, 1, fallback,
                minusOne, zero, one));
        method.instructions.add(minusOne);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(zero);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(one);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(fallback);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode lookupSwitchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "lookupSelect", "(II)I", null, null);
        LabelNode negative = new LabelNode();
        LabelNode positive = new LabelNode();
        LabelNode fallback = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new LookupSwitchInsnNode(fallback,
                new int[]{-7, 42}, new LabelNode[]{negative, positive}));
        method.instructions.add(negative);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(positive);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(fallback);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode switchDefaultCarrierMismatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "badSwitchDefault", "(Ljava/lang/Object;I)V", null, null);
        LabelNode alternate = new LabelNode();
        LabelNode intCase = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, alternate));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new TableSwitchInsnNode(0, 0, join, intCase));
        method.instructions.add(alternate);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(intCase);
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 4;
        method.maxStack = 2;
        return method;
    }

    private MethodNode newObjectArrayCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "allocateStrings", "(I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -13));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/NegativeArraySizeException"));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode newObjectArrayMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "allocateObjects", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode newNestedObjectArrayMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "allocateStringRows", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY,
                "[Ljava/lang/String;"));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unsupportedAfterObjectArrayMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterObjectArray", "(I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        method.instructions.add(new InsnNode(Opcodes.POP2));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode arrayBoundsCatchMethod(String name, String catchType) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "([I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IALOAD));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -7));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, catchType));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode explicitThrowCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchThrown", "(Ljava/lang/Throwable;)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -9));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/Throwable"));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode sumToMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "sumTo", "(I)I", null, null);
        LabelNode header = new LabelNode();
        LabelNode exit = new LabelNode();

        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(header);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, exit));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(new IincInsnNode(2, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, header));
        method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode subMulMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "subMul", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.ISUB));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IMUL));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode divRemMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "divRem", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IREM));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode divideCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchDivide", "(II)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IDIV));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -11));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArithmeticException"));
        method.maxLocals = 3;
        method.maxStack = 2;
        return method;
    }

    private MethodNode checkCastInstanceOfMethod(String name, String targetType) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, targetType));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, targetType));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode checkCastCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchCast", "(Ljava/lang/Object;)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"));
        method.instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/lang/String"));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 1));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, -15));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ClassCastException"));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode longArithmeticMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longArithmetic", "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 4));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 4));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new InsnNode(Opcodes.LCONST_1));
        method.instructions.add(new InsnNode(Opcodes.LSUB));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.LMUL));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 6;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longBitwiseShiftMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longBitwiseShift", "(JJI)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LAND));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LOR));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LXOR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LSHL));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LSHR));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LUSHR));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 5;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longDivRemMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longDivRem", "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LDIV));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 4));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LREM));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 4));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 6;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longNegateMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longNegate", "(J)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.LNEG));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode longDivideCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "catchLongDivide", "(JJ)J", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LDIV));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 4));
        method.instructions.add(new LdcInsnNode(-11L));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArithmeticException"));
        method.maxLocals = 5;
        method.maxStack = 4;
        return method;
    }

    private MethodNode wrappingLongShiftMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "wrappingLongShift", "()J", null, null);
        method.instructions.add(new LdcInsnNode(Long.MAX_VALUE));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 65));
        method.instructions.add(new InsnNode(Opcodes.LSHL));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 0;
        method.maxStack = 3;
        return method;
    }

    private MethodNode longConversionMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longConversion", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.I2L));
        method.instructions.add(new InsnNode(Opcodes.LCONST_1));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new InsnNode(Opcodes.L2I));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longCompareMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longCompare", "(JJ)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LCMP));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 4;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longCompareBranchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longCompareBranch", "(JJ)I", null, null);
        LabelNode notLess = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.LCMP));
        method.instructions.add(new JumpInsnNode(Opcodes.IFGE, notLess));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(notLess);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 4;
        method.maxStack = 4;
        return method;
    }

    private MethodNode wideStackPhiMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "widePhi", "(JI)J", null, null);
        LabelNode alternate = new LabelNode();
        LabelNode join = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, alternate));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, join));
        method.instructions.add(alternate);
        method.instructions.add(new InsnNode(Opcodes.LCONST_1));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(join);
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 3));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 4;
        method.maxStack = 3;
        return method;
    }

    private MethodNode explicitMonitorMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "explicitMonitor", "(Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.MONITORENTER));
        method.instructions.add(new InsnNode(Opcodes.MONITOREXIT));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode nullMonitorCatchMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "nullMonitor", "()I", null, null);
        LabelNode start = new LabelNode();
        LabelNode entered = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new InsnNode(Opcodes.MONITORENTER));
        method.instructions.add(entered);
        method.instructions.add(new InsnNode(Opcodes.MONITOREXIT));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, entered, handler, null));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode synchronizedStaticCounterMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
                "synchronizedStaticCounter", "(I)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode synchronizedInstanceMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_SYNCHRONIZED,
                "synchronizedInstance", "(I)I", null, null);
        LabelNode normal = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, normal));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(normal);
        method.instructions.add(new InsnNode(Opcodes.ICONST_5));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unstructuredMonitorMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unstructuredMonitor", "(Ljava/lang/Object;)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.MONITORENTER));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unsupportedWideOperationMethod() {
        // Monitors, INVOKEDYNAMIC, and proven ConstantDynamic shapes are now
        // admitted. Legacy subroutines remain outside the IR subset.
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedWide", "()V", null, null);
        appendStillUnsupportedConstruct(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unlowerableConstantDynamicMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "condy", "()V", null, null);
        Handle bootstrap = new Handle(Opcodes.H_INVOKEVIRTUAL,
                "example/Bootstrap", "constant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;", false);
        method.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "constant", "Ljava/lang/String;", bootstrap)));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private ClassNode interfaceConstantDynamicCounter(String name) {
        ClassNode counter = new ClassNode(Opcodes.ASM9);
        counter.version = Opcodes.V11;
        counter.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        counter.name = name;
        counter.superName = "java/lang/Object";
        counter.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "stringCalls", "I", null, null));
        counter.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "intCalls", "I", null, null));

        MethodNode string = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "stringValue",
                "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;",
                null, null);
        string.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, name, "stringCalls", "I"));
        string.instructions.add(new InsnNode(Opcodes.ICONST_1));
        string.instructions.add(new InsnNode(Opcodes.IADD));
        string.instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC, name, "stringCalls", "I"));
        string.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        string.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandles$Lookup", "lookupClass",
                "()Ljava/lang/Class;", false));
        string.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName",
                "()Ljava/lang/String;", false));
        string.instructions.add(new InsnNode(Opcodes.ARETURN));
        string.maxLocals = 1;
        string.maxStack = 2;
        counter.methods.add(string);

        MethodNode integer = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "intValue",
                "(Ljava/lang/invoke/MethodHandles$Lookup;)I",
                null, null);
        integer.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC, name, "intCalls", "I"));
        integer.instructions.add(new InsnNode(Opcodes.ICONST_1));
        integer.instructions.add(new InsnNode(Opcodes.IADD));
        integer.instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC, name, "intCalls", "I"));
        integer.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 21));
        integer.instructions.add(new InsnNode(Opcodes.IRETURN));
        integer.maxLocals = 1;
        integer.maxStack = 2;
        counter.methods.add(integer);
        return counter;
    }

    private ClassNode interfaceConstantDynamicOwner(
            String name, String counterName) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V11;
        owner.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE
                | Opcodes.ACC_ABSTRACT;
        owner.name = name;
        owner.superName = "java/lang/Object";

        MethodNode stringBootstrap = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "stringBootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;",
                null, null);
        stringBootstrap.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        stringBootstrap.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, counterName, "stringValue",
                "(Ljava/lang/invoke/MethodHandles$Lookup;)Ljava/lang/String;",
                false));
        stringBootstrap.instructions.add(new InsnNode(Opcodes.ARETURN));
        stringBootstrap.maxLocals = 3;
        stringBootstrap.maxStack = 1;
        owner.methods.add(stringBootstrap);

        MethodNode intBootstrap = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "intBootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)I",
                null, null);
        intBootstrap.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        intBootstrap.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC, counterName, "intValue",
                "(Ljava/lang/invoke/MethodHandles$Lookup;)I", false));
        intBootstrap.instructions.add(new InsnNode(Opcodes.IRETURN));
        intBootstrap.maxLocals = 3;
        intBootstrap.maxStack = 1;
        owner.methods.add(intBootstrap);
        return owner;
    }

    private MethodNode interfaceConstantDynamicCombinedMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "combined", "()I", null, null);
        method.instructions.add(new LdcInsnNode(
                interfaceStringConstant(owner)));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/lang/String", "length",
                "()I", false));
        method.instructions.add(new LdcInsnNode(interfaceIntConstant(owner)));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private ConstantDynamic interfaceStringConstant(String owner) {
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "stringBootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;",
                true);
        return new ConstantDynamic(
                "interfaceString", "Ljava/lang/String;", bootstrap);
    }

    private ConstantDynamic interfaceIntConstant(String owner) {
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "intBootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)I",
                true);
        return new ConstantDynamic("interfaceInt", "I", bootstrap);
    }

    private MethodNode interfaceConstantDynamicStringMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "stringValue", "()Ljava/lang/String;", null, null);
        ConstantDynamic constant = interfaceStringConstant(owner);
        method.instructions.add(new LdcInsnNode(constant));
        method.instructions.add(new LdcInsnNode(constant));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat",
                "(Ljava/lang/String;)Ljava/lang/String;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private MethodNode interfaceConstantDynamicIntMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "intValue", "()I", null, null);
        ConstantDynamic constant = interfaceIntConstant(owner);
        method.instructions.add(new LdcInsnNode(constant));
        method.instructions.add(new LdcInsnNode(constant));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 2;
        return method;
    }

    private ClassNode constantDynamicOwner() {
        ClassNode owner = owner();
        owner.version = Opcodes.V11;
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bootstrapCalls", "I", null, null));
        owner.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "outerBootstrapCalls", "I", null, null));

        MethodNode bootstrap = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "stringConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;",
                null, null);
        bootstrap.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                owner.name, "bootstrapCalls", "I"));
        bootstrap.instructions.add(new InsnNode(Opcodes.ICONST_1));
        bootstrap.instructions.add(new InsnNode(Opcodes.IADD));
        bootstrap.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                owner.name, "bootstrapCalls", "I"));
        bootstrap.instructions.add(new LdcInsnNode("condy-value"));
        bootstrap.instructions.add(new InsnNode(Opcodes.ARETURN));
        bootstrap.maxLocals = 3;
        bootstrap.maxStack = 2;
        owner.methods.add(bootstrap);

        MethodNode outerBootstrap = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "nestedConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;",
                null, null);
        outerBootstrap.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                owner.name, "outerBootstrapCalls", "I"));
        outerBootstrap.instructions.add(new InsnNode(Opcodes.ICONST_1));
        outerBootstrap.instructions.add(new InsnNode(Opcodes.IADD));
        outerBootstrap.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                owner.name, "outerBootstrapCalls", "I"));
        outerBootstrap.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
        outerBootstrap.instructions.add(new InsnNode(Opcodes.ARETURN));
        outerBootstrap.maxLocals = 4;
        outerBootstrap.maxStack = 2;
        owner.methods.add(outerBootstrap);

        MethodNode intBootstrap = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "intConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)I",
                null, null);
        intBootstrap.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 42));
        intBootstrap.instructions.add(new InsnNode(Opcodes.IRETURN));
        intBootstrap.maxLocals = 3;
        intBootstrap.maxStack = 1;
        owner.methods.add(intBootstrap);
        return owner;
    }

    private MethodNode constantDynamicStringMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "condyString", "()I", null, null);
        Handle nestedBootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "stringConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;", false);
        ConstantDynamic nested = new ConstantDynamic(
                "nestedMessage", "Ljava/lang/String;", nestedBootstrap);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "nestedConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/String;",
                false);
        method.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "message", "Ljava/lang/String;", bootstrap, nested)));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "length", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode constantDynamicIntMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "condyInt", "()I", null, null);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "intConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)I", false);
        method.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "answer", "I", bootstrap)));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode constantDynamicCatchMethod(String owner) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "condyCatch", "()I", null, null);
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, owner,
                "stringConstant",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                        + "Ljava/lang/Class;)Ljava/lang/String;", false);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new LdcInsnNode(new ConstantDynamic(
                "message", "Ljava/lang/String;", bootstrap)));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.add(new InsnNode(Opcodes.ICONST_2));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(
                start, end, handler, "java/lang/BootstrapMethodError"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode rawMethodTypeUseMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "rawMethodType", "()I", null, null);
        method.instructions.add(new LdcInsnNode(
                Type.getMethodType("(Ljava/lang/String;I)V")));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodType", "parameterCount", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private Path executableOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String entry : path.split(File.pathSeparator)) {
            Path candidate = Paths.get(entry).resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String jniPlatformDirectory() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return "linux";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "darwin";
        }
        if (os.contains("win")) {
            return "win32";
        }
        return os;
    }

    private MethodNode addMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "add", "(II)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private Class<?> define(byte[] bytecode) {
            return defineClass(null, bytecode, 0, bytecode.length);
        }
    }
}
