package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.ir.emit.IrCppEmitter;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.AsmToIr;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IrCompilerTest {
    private final AsmToIr frontend = new AsmToIr();
    private final IrCppEmitter emitter = new IrCppEmitter();

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
    public void rejectsNonIntStaticFieldBeforeMutation() {
        MethodNode method = unsupportedStaticReferenceFieldMethod();
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(), 0);

        UnsupportedIrConstructException error = assertThrows(
                UnsupportedIrConstructException.class,
                () -> new IrMethodCompiler(new MethodShellEmitter(obfuscator))
                        .processMethod(context));

        assertEquals(Opcodes.GETSTATIC, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
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

        assertEquals(Opcodes.LDIV, error.getOpcode());
        assertEquals(0, method.access & Opcodes.ACC_NATIVE);
        assertEquals("", context.output.toString());
        assertEquals("", context.nativeMethods.toString());
        assertEquals(0, obfuscator.getCachedClasses().size());
        assertEquals(0, obfuscator.getCachedStrings().size());
        assertEquals(0, obfuscator.getCachedFields().size());
        assertEquals(0, obfuscator.getCachedMethods().size());
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
        Assumptions.assumeTrue(gpp != null, "g++ is not available");
        Assumptions.assumeTrue(Files.isRegularFile(jniInclude.resolve("jni.h")),
                "JNI headers are not available");
        Assumptions.assumeTrue(Files.isDirectory(platformInclude),
                "Platform JNI headers are not available");

        NativeObfuscator obfuscator = new NativeObfuscator();
        ClassNode owner = owner();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));
        MethodNode[] methods = {
                addMethod(), sumToMethod(), subMulMethod(), incrementFieldMethod(),
                staticInvokeMethod(), virtualInvokeMethod(), bitwiseShiftMethod(),
                unaryMethod(), intArrayMethod(), stringLengthMethod(),
                arrayBoundsCatchMethod("catchBounds", "java/lang/ArrayIndexOutOfBoundsException"),
                arrayBoundsCatchMethod("rethrowBounds", "java/lang/NullPointerException"),
                explicitThrowCatchMethod(), arrayBoundsCatchMethod("catchAny", null),
                divRemMethod(), divideCatchMethod(), newIntArrayCatchMethod(),
                staticIntFieldMethod(), tableSwitchMethod(), lookupSwitchMethod(),
                newObjectArrayCatchMethod(), newObjectArrayMethod(),
                newNestedObjectArrayMethod(),
                checkCastInstanceOfMethod("typeTest", "java/lang/String"),
                checkCastInstanceOfMethod("arrayTypeTest", "[Ljava/lang/String;"),
                checkCastCatchMethod(), longArithmeticMethod(), longConversionMethod(),
                wideStackPhiMethod(), constructObjectMethod(), staticLongInvokeMethod(),
                virtualStringInvokeMethod(), staticStringInvokeMethod(),
                staticVoidLongInvokeMethod(), returnAllocatedObjectMethod(),
                returnNullMethod(), referenceNullBranchMethod("ifNull", Opcodes.IFNULL),
                referenceNullBranchMethod("ifNonNull", Opcodes.IFNONNULL),
                popUnusedCategoryOneInvokeResultMethod()
        };
        StringBuilder generatedFunctions = new StringBuilder();
        for (int i = 0; i < methods.length; i++) {
            MethodContext context = new MethodContext(obfuscator, methods[i], i, owner, 0);
            compiler.processMethod(context);
            generatedFunctions.append(context.output);
        }

        String source = "#include <jni.h>\n"
                + "#include <cstdint>\n"
                + "#include <mutex>\n"
                + "#include <unordered_set>\n"
                + "namespace native_jvm {\n"
                + "namespace utils {\n"
                + "void throw_re(JNIEnv *, const char *, const char *, int);\n"
                + "jclass find_class_wo_static(JNIEnv *, jobject, jstring);\n"
                + "jclass get_class_from_object(JNIEnv *, jobject);\n"
                + "jobject get_classloader_from_class(JNIEnv *, jclass);\n"
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
        assertTrue(source.contains("IR codegen: example/Math.longConversion(I)I"));
        assertTrue(source.contains("IR codegen: example/Math.widePhi(JI)J"));
        assertTrue(source.contains("IR codegen: example/Math.constructObject()I"));
        assertTrue(source.contains("env->AllocObject(cclasses["));
        assertTrue(source.contains("env->CallNonvirtualVoidMethod"));
        assertTrue(source.contains("env->CallStaticLongMethod"));
        assertTrue(source.contains("env->CallObjectMethod"));
        assertTrue(source.contains("env->CallStaticObjectMethod"));
        assertTrue(source.contains("env->CallStaticVoidMethod"));
        assertTrue(source.contains(
                "IR codegen: example/Math.returnAllocatedObject()Ljava/lang/Object;"));
        assertTrue(source.contains("IR codegen: example/Math.returnNull()Ljava/lang/Object;"));
        assertTrue(source.contains("IR codegen: example/Math.ifNull"));
        assertTrue(source.contains("if (arg0 == nullptr) {"));
        assertTrue(source.contains("IR codegen: example/Math.ifNonNull"));
        assertTrue(source.contains("if (arg0 != nullptr) {"));
        assertTrue(source.contains("IR codegen: example/Math.discardInvokeResult()V"));
        assertTrue(source.contains("env->IsInstanceOf(arg0"));
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
        return owner;
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

    private MethodNode unsupportedStaticReferenceFieldMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedStaticReference", "()I", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC,
                "example/Math", "object", "Ljava/lang/Object;"));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 0;
        method.maxStack = 1;
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
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
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
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "unsupportedWide", "(J)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.LCONST_1));
        method.instructions.add(new InsnNode(Opcodes.LMUL));
        method.instructions.add(new InsnNode(Opcodes.LCONST_1));
        method.instructions.add(new InsnNode(Opcodes.LDIV));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 2;
        method.maxStack = 4;
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
}
