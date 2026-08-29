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
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import by.radioegor146.source.ClassSourceBuilder;
import by.radioegor146.special.ConstructorSpecialMethodProcessor;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IrCompilerTest {
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
    public void rejectsMultipleThisOrSuperCandidates() {
        ClassNode owner = constructorOwner("example/Twice", "java/lang/Object");
        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> ConstructorSpecialMethodProcessor.createNativeBody(
                        owner, multipleSuperCallConstructor()));
        assertEquals(Opcodes.INVOKESPECIAL, error.getOpcode());
        assertTrue(error.getMessage().contains(
                "Constructor has multiple possible this/super calls"));
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
    }

    @Test
    public void rejectsPrefixWritesToForwardedReferenceLocalsBeforeMutation()
            throws Exception {
        Class<?> parameterClass = rejectConstructorPrefixReferenceWrite(
                "example/ParameterPrefix",
                referenceParameterReassignedBeforeSuperConstructor());
        parameterClass.getConstructor(String.class, Object.class)
                .newInstance("value", new Object());

        Class<?> receiverClass = rejectConstructorPrefixReferenceWrite(
                "example/ReceiverPrefix",
                receiverReassignedBeforeSuperConstructor());
        receiverClass.getConstructor(Object.class).newInstance(new Object());
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
    public void rejectsInvokedynamicBeforeMutation() {
        MethodNode method = invokedynamicMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
        assertUnchangedAfterRejectedIr(method, context, obfuscator);
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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
    public void rejectsPrimitiveClassLdcBeforeMutation() {
        MethodNode method = primitiveClassLdcMethod();
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
    public void rejectsHandleLdcAfterAdmittedPhaseFifteenConstantsBeforeMutation() {
        MethodNode method = unsupportedAfterPhaseFifteenLdcMethod();
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.INVOKEDYNAMIC, error.getOpcode());
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

        assertEquals(Opcodes.IF_ACMPEQ, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
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
                stringLdcMethod(), classLdcMethod(), longLdcMethod(),
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

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int position = 0;
        while ((position = value.indexOf(needle, position)) >= 0) {
            count++;
            position += needle.length();
        }
        return count;
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

    private Class<?> rejectConstructorPrefixReferenceWrite(
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
        assertUnchangedAfterRejectedIr(constructor, context, obfuscator);
        assertEquals(instructionCount, constructor.instructions.size());
        assertEquals(opcodes, realOpcodes(constructor));
        assertTrue(context.proxyMethod == null);
        assertTrue(obfuscator.getHiddenMethodsPool().getClasses().isEmpty());
        return new ByteArrayClassLoader().define(writeClass(owner));
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

    private void appendUnsupportedInvokeDynamic(MethodNode method) {
        appendUnsupportedInvokeDynamic(method, "()V");
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
                "primitiveClass", "()Ljava/lang/Class;", null, null);
        method.instructions.add(new LdcInsnNode(Type.INT_TYPE));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
        return method;
    }

    private MethodNode unsupportedAfterPhaseFifteenLdcMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedAfterPhaseFifteenLdc", "()V", null, null);
        method.instructions.add(new LdcInsnNode(""));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(Type.getObjectType("java/lang/String")));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new LdcInsnNode(0x1_0000_0000L));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 0));
        method.instructions.add(new LdcInsnNode(new Handle(Opcodes.H_INVOKESTATIC,
                "example/Bootstrap", "target", "()V", false)));
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
        appendUnsupportedInvokeDynamic(method);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
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
        appendUnsupportedInvokeDynamic(method);
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
        appendUnsupportedInvokeDynamic(method);
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
        appendUnsupportedInvokeDynamic(method);
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
        appendUnsupportedInvokeDynamic(method);
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
        appendUnsupportedInvokeDynamic(method);
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

        appendUnsupportedInvokeDynamic(method);
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
        appendUnsupportedInvokeDynamic(method);
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

    private MethodNode unsupportedWideOperationMethod() {
        // LCMP, the previous sentinel here, is now admitted by the IR
        // frontend. IF_ACMPEQ (a reference compare branch) is still outside
        // the supported subset, so it keeps proving that rejection happens
        // before any mutation.
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedWide", "(Ljava/lang/Object;Ljava/lang/Object;)I",
                null, null);
        LabelNode same = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ACMPEQ, same));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(same);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
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
