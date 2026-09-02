package by.radioegor146.ir;

import by.radioegor146.MethodContext;
import by.radioegor146.NativeObfuscator;
import by.radioegor146.ir.emit.MethodShellEmitter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JniCacheHoistTest {
    @Test
    public void ownClassFieldLoopDoesNotRecheckCache() {
        String cpp = emit(fieldIncrementLoop());
        int getFieldId = cpp.indexOf("GetFieldID");
        int loopGet = cpp.indexOf("GetIntField");
        int loopSet = cpp.indexOf("SetIntField", loopGet);
        assertTrue(getFieldId >= 0 && loopGet > getFieldId && loopSet > loopGet);
        String hot = cpp.substring(loopGet, cpp.indexOf(';', loopSet) + 1);
        assertFalse(hot.contains("IsSameObject"), hot);
        assertFalse(hot.contains("GetFieldID"), hot);
        assertFalse(hot.contains("ExceptionCheck"), hot);
        int firstLabel = cpp.indexOf("B0:");
        assertTrue(firstLabel > getFieldId);
    }

    @Test
    public void parameterArrayLoopLoadsLengthOnceBeforeHotStores() {
        String cpp = emit(intArrayIncrementLoop());
        assertTrue(cpp.contains("arr_len_cached_array"));
        int load = cpp.indexOf("GetIntArrayElements");
        int store = cpp.indexOf("pin_int_elems");
        assertTrue(load >= 0 && store >= 0);
        assertFalse(cpp.contains("IsSameObject"), cpp);
        assertFalse(cpp.contains("jsize array_len"), cpp);
        assertTrue(cpp.contains("arr_len_cached = env->GetArrayLength"));
    }

    @Test
    public void ownClassIntFieldLoopBuffersStoresUntilReturn() {
        String cpp = emit(fieldIncrementLoop());
        assertTrue(cpp.contains("fld_cached_i"));
        assertTrue(cpp.contains("fld_cached_dirty = 1"));
        assertTrue(cpp.contains("GetIntField"));
        assertTrue(cpp.contains("SetIntField"));
        int buffered = cpp.indexOf("fld_cached_dirty = 1");
        int nearbySet = cpp.lastIndexOf("SetIntField", buffered);
        assertTrue(buffered >= 0);
        assertTrue(nearbySet < 0 || buffered - nearbySet > 80, cpp);
    }

    @Test
    public void intArrayLoopPinsElementsInsteadOfPerAccessRegion() {
        String cpp = emit(intArrayIncrementLoop());
        assertTrue(cpp.contains("GetIntArrayElements"));
        assertFalse(cpp.contains("GetIntArrayRegion"));
        assertFalse(cpp.contains("SetIntArrayRegion"));
        assertTrue(cpp.contains("pin_int_elems"));
        assertTrue(cpp.contains("ReleaseIntArrayElements"));
    }

    @Test
    public void methodHandleInvokeHelperStillLooksUpId() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call",
                "(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;)Ljava/lang/String;",
                null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandle", "invokeExact",
                "(Ljava/lang/String;)Ljava/lang/String;", false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 2;
        method.maxStack = 2;
        String cpp = emit(method);
        assertTrue(cpp.contains("GetStaticMethodID") || cpp.contains("GetMethodID"), cpp);
        assertTrue(cpp.contains("CallStatic"), cpp);
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

    private MethodNode fieldIncrementLoop() {
        MethodNode method = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PUBLIC,
                "bump", "(I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode done = new LabelNode();
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.IFLE, done));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new IincInsnNode(1, -1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, start));
        method.instructions.add(done);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,
                "example/Math", "value", "I"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    private MethodNode intArrayIncrementLoop() {
        MethodNode method = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bump", "([I)I", null, null);
        LabelNode start = new LabelNode();
        LabelNode done = new LabelNode();
        method.instructions.add(new InsnNode(Opcodes.ICONST_0));
        method.instructions.add(new VarInsnNode(Opcodes.ISTORE, 1));
        method.instructions.add(start);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new JumpInsnNode(Opcodes.IF_ICMPGE, done));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new InsnNode(Opcodes.IALOAD));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IADD));
        method.instructions.add(new InsnNode(Opcodes.IASTORE));
        method.instructions.add(new IincInsnNode(1, 1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO, start));
        method.instructions.add(done);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.ARRAYLENGTH));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxLocals = 2;
        method.maxStack = 4;
        return method;
    }
}
