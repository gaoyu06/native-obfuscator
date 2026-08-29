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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.IincInsnNode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public void buildsTwoSlotLongLocalsAndEmitsWrappingI64Operations() {
        IrMethod ir = frontend.build("example/Math", longMixMethod());
        String text = ir.toString();
        String cpp = emitter.emitBody(ir);

        assertTrue(text.contains("method example/Math.longMix(JI)J -> i64 [static]"));
        assertTrue(text.contains("%arg0:i64, %arg1:i32"));
        assertTrue(text.contains(" = i2l %arg1"));
        assertTrue(text.contains(" = ladd "));
        assertTrue(text.contains(" = lmul "));
        assertTrue(cpp.contains("jlong v2;"));
        assertTrue(cpp.contains("(uint64_t) arg0 + (uint64_t) v2"));
        assertTrue(cpp.contains("(uint64_t) v3 * (uint64_t) arg0"));
        assertTrue(cpp.contains("return v4;"));
    }

    @Test
    public void buildsAndEmitsJvmLongDivisionAndRemainder() {
        MethodNode divide = longBinaryMethod("divide", Opcodes.LDIV);
        MethodNode remainder = longBinaryMethod("remainder", Opcodes.LREM);
        assertTrue(frontend.build("example/Math", divide).toString().contains(" = ldiv "));
        assertTrue(frontend.build("example/Math", remainder).toString().contains(" = lrem "));

        NativeObfuscator obfuscator = new NativeObfuscator();
        IrMethodCompiler compiler = new IrMethodCompiler(new MethodShellEmitter(obfuscator));
        MethodContext divideContext = new MethodContext(obfuscator, divide, 0, owner(), 0);
        MethodContext remainderContext = new MethodContext(
                obfuscator, remainder, 1, owner(), 0);
        compiler.processMethod(divideContext);
        compiler.processMethod(remainderContext);

        String divideCpp = divideContext.output.toString();
        assertTrue(divideCpp.contains("utils::throw_re(env"));
        assertTrue(divideCpp.contains("(arg1 == 0LL)"));
        assertTrue(divideCpp.contains("(arg0 == ((jlong) 0x8000000000000000ULL))"));
        assertTrue(divideCpp.contains("(arg1 == -1LL)"));
        assertTrue(divideCpp.contains("v2 = arg0;"));
        assertTrue(divideCpp.contains("v2 = (arg0 / arg1);"));

        String remainderCpp = remainderContext.output.toString();
        assertTrue(remainderCpp.contains("utils::throw_re(env"));
        assertTrue(remainderCpp.contains("v2 = 0LL;"));
        assertTrue(remainderCpp.contains("v2 = (arg0 % arg1);"));
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
                longBinaryMethod("divide", Opcodes.LDIV),
                longBinaryMethod("remainder", Opcodes.LREM),
                arrayBoundsCatchMethod("catchBounds", "java/lang/ArrayIndexOutOfBoundsException"),
                arrayBoundsCatchMethod("rethrowBounds", "java/lang/NullPointerException"),
                explicitThrowCatchMethod(), arrayBoundsCatchMethod("catchAny", null)
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

    private MethodNode longMixMethod() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "longMix", "(JI)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new InsnNode(Opcodes.I2L));
        method.instructions.add(new InsnNode(Opcodes.LADD));
        method.instructions.add(new VarInsnNode(Opcodes.LSTORE, 3));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.LMUL));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 5;
        method.maxStack = 4;
        return method;
    }

    private MethodNode longBinaryMethod(String name, int opcode) {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                name, "(JJ)J", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.LLOAD, 2));
        method.instructions.add(new InsnNode(opcode));
        method.instructions.add(new InsnNode(Opcodes.LRETURN));
        method.maxLocals = 4;
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
