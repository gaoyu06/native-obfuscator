package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.ir.emit.MethodShellEmitter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExceptionCheckContractionTest {
    @Test
    public void instanceFieldAccessDoesNotPollAfterAccessor() {
        String cpp = emit(instanceIntFieldRoundTrip());
        int get = cpp.indexOf("env->GetIntField");
        int set = cpp.indexOf("env->SetIntField");
        assertTrue(get >= 0 && set >= 0);
        int getEnd = cpp.indexOf(';', get);
        int setEnd = cpp.indexOf(';', set);
        assertTrue(getEnd > get && setEnd > set);
        assertFalse(cpp.substring(get, getEnd + 1).contains("ExceptionCheck"));
        assertFalse(cpp.substring(set, setEnd + 1).contains("ExceptionCheck"));
    }

    @Test
    public void allocationFailureUsesNullReturnNotExceptionCheck() {
        String cpp = emit(newIntArrayCatch());
        int allocation = cpp.indexOf("env->NewIntArray");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", allocation);
        assertTrue(allocation >= 0 && dispatch > allocation);
        String slice = cpp.substring(allocation, dispatch);
        assertTrue(slice.contains("== nullptr"));
        assertFalse(slice.contains("ExceptionCheck"));
    }

    @Test
    public void invokeThenReturnOmitsPendingPoll() {
        String cpp = emit(hashCodeThenReturn());
        int call = cpp.lastIndexOf("CallIntMethod");
        int ret = cpp.indexOf("return", call);
        assertTrue(call >= 0 && ret > call);
        assertFalse(cpp.substring(call, ret).contains("ExceptionCheck"));
    }

    @Test
    public void invokeThenInvokePollsBetweenCalls() {
        String cpp = emit(hashCodeTwice());
        int first = cpp.indexOf("CallIntMethod");
        int second = cpp.indexOf("CallIntMethod", first + 1);
        assertTrue(first >= 0 && second > first);
        assertTrue(cpp.substring(first, second).contains("ExceptionCheck"));
        int ret = cpp.indexOf("return", second);
        assertTrue(ret > second);
        assertFalse(cpp.substring(second, ret).contains("ExceptionCheck"));
    }

    @Test
    public void invokeWithCatchStillPolls() {
        String cpp = emit(hashCodeCatch());
        int call = cpp.indexOf("CallIntMethod");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", call);
        assertTrue(call >= 0 && dispatch > call);
        assertTrue(cpp.substring(call, dispatch).contains("ExceptionCheck"));
    }

    @Test
    public void athrowDispatchesWithoutExceptionCheck() {
        String cpp = emit(explicitThrowCatch());
        int throwCall = cpp.indexOf("env->Throw(");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;", throwCall);
        assertTrue(throwCall >= 0 && dispatch > throwCall);
        assertFalse(cpp.substring(throwCall, dispatch).contains("ExceptionCheck"));
    }

    @Test
    public void arrayLoadUsesCppBoundsInsteadOfRegionExceptionCheck() {
        String cpp = emit(intArrayLoadCatch());
        assertTrue(cpp.contains("GetArrayLength"));
        assertTrue(cpp.contains("ArrayIndexOutOfBoundsException"));
        int pin = cpp.indexOf("GetIntArrayElements");
        int dispatch = cpp.indexOf("goto IR_CATCH_0;");
        assertTrue(pin >= 0 && dispatch >= 0);
        int oob = cpp.indexOf("ArrayIndexOutOfBoundsException");
        assertTrue(oob >= 0 && cpp.indexOf("goto IR_CATCH_0;", oob) > oob);
        assertFalse(cpp.substring(pin).contains("ExceptionCheck()"));
    }

    @Test
    public void helperIntrinsicsUseBooleanFailureInsteadOfOuterPoll() {
        String hash = emit(stringHash());
        assertTrue(hash.contains("utils::string_hash_code"));
        assertTrue(hash.contains("(!utils::string_hash_code"));
        int hashCall = hash.indexOf("utils::string_hash_code");
        int hashRet = hash.indexOf("return", hashCall);
        assertTrue(hashRet > hashCall);
        assertFalse(hash.substring(hashCall, hashRet).contains("ExceptionCheck"));

        String copy = emit(arraycopy());
        assertTrue(copy.contains("(!utils::arraycopy"));
        int copyCall = copy.indexOf("utils::arraycopy");
        int copyRet = copy.indexOf("return", copyCall);
        assertTrue(copyRet > copyCall);
        assertFalse(copy.substring(copyCall, copyRet).contains("ExceptionCheck"));
    }

    @Test
    public void memberIdLookupUsesNullReturn() {
        String cpp = emit(hashCodeThenReturn());
        int lookup = cpp.indexOf("GetMethodID");
        assertTrue(lookup >= 0);
        int after = cpp.indexOf("CallIntMethod", lookup);
        assertTrue(after > lookup);
        String slice = cpp.substring(lookup, after);
        assertTrue(slice.contains("== nullptr") || slice.contains("!cmethods"));
        assertFalse(slice.contains("ExceptionCheck"));
    }

    private String emit(MethodNode method) {
        NativeObfuscator obfuscator = new NativeObfuscator();
        MethodContext context = new MethodContext(obfuscator, method, 0, owner(method), 0);
        new IrMethodCompiler(new MethodShellEmitter(obfuscator)).processMethod(context);
        return context.output.toString();
    }

    private ClassNode owner(MethodNode method) {
        ClassNode owner = new ClassNode(Opcodes.ASM9);
        owner.version = Opcodes.V1_8;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = "example/Math";
        owner.superName = "java/lang/Object";
        owner.fields.add(new FieldNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "value", "I", null, null));
        owner.methods.add(method);
        return owner;
    }

    private MethodNode instanceIntFieldRoundTrip() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "bump", "()I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 3;
        return method;
    }

    private MethodNode newIntArrayCatch() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "alloc", "(I)[I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 0));
        method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.NEWARRAY,
                Opcodes.T_INT));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/OutOfMemoryError"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode hashCodeThenReturn() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "hash", "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode hashCodeTwice() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "hashTwice", "(Ljava/lang/Object;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        return method;
    }

    private MethodNode hashCodeCatch() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "hashCatch", "(Ljava/lang/Object;)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/Object", "hashCode", "()I", false));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/RuntimeException"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode explicitThrowCatch() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "rethrow", "(Ljava/lang/Throwable;)I", null, null);
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
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/Throwable"));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode intArrayLoadCatch() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                "load", "([II)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IALOAD));
        method.instructions.add(end);
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(handler);
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.ICONST_M1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler,
                "java/lang/ArrayIndexOutOfBoundsException"));
        method.maxLocals = 2;
        method.maxStack = 2;
        return method;
    }

    private MethodNode stringHash() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "hash", "(Ljava/lang/String;)I", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "hashCode", "()I", false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 1;
        method.maxStack = 1;
        return method;
    }

    private MethodNode arraycopy() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "copy", "(Ljava/lang/Object;ILjava/lang/Object;II)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxLocals = 5;
        method.maxStack = 5;
        return method;
    }
}
