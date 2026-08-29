package by.radioegor146;

import by.radioegor146.instructions.*;
import by.radioegor146.ir.emit.MethodShellEmitter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Field;
import java.util.*;

public class MethodProcessor {

    public static final Map<Integer, String> INSTRUCTIONS = new HashMap<>();

    static {
        try {
            for (Field f : Opcodes.class.getFields()) {
                INSTRUCTIONS.put((int) f.get(null), f.getName());
            }
        } catch (IllegalArgumentException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static final String[] CPP_TYPES = {
            "void", // 0
            "jboolean", // 1
            "jchar", // 2
            "jbyte", // 3
            "jshort", // 4
            "jint", // 5
            "jfloat", // 6
            "jlong", // 7
            "jdouble", // 8
            "jarray", // 9
            "jobject", // 10
            "jobject" // 11
    };

    public static final int[] TYPE_TO_STACK = {
            1, 1, 1, 1, 1, 1, 1, 2, 2, 0, 0, 0
    };

    public static final int[] STACK_TO_STACK = {
            1, 1, 1, 2, 2, 0, 0, 0, 0
    };

    private final NativeObfuscator obfuscator;
    private final MethodShellEmitter shellEmitter;
    private final InstructionHandlerContainer<?>[] handlers;

    public MethodProcessor(NativeObfuscator obfuscator) {
        this(obfuscator, new MethodShellEmitter(obfuscator));
    }

    public MethodProcessor(NativeObfuscator obfuscator, MethodShellEmitter shellEmitter) {
        this.obfuscator = obfuscator;
        this.shellEmitter = shellEmitter;

        handlers = new InstructionHandlerContainer[16];
        addHandler(AbstractInsnNode.INSN, new InsnHandler(), InsnNode.class);
        addHandler(AbstractInsnNode.INT_INSN, new IntHandler(), IntInsnNode.class);
        addHandler(AbstractInsnNode.VAR_INSN, new VarHandler(), VarInsnNode.class);
        addHandler(AbstractInsnNode.TYPE_INSN, new TypeHandler(), TypeInsnNode.class);
        addHandler(AbstractInsnNode.FIELD_INSN, new FieldHandler(), FieldInsnNode.class);
        addHandler(AbstractInsnNode.METHOD_INSN, new MethodHandler(), MethodInsnNode.class);
        addHandler(AbstractInsnNode.INVOKE_DYNAMIC_INSN, new InvokeDynamicHandler(), InvokeDynamicInsnNode.class);
        addHandler(AbstractInsnNode.JUMP_INSN, new JumpHandler(), JumpInsnNode.class);
        addHandler(AbstractInsnNode.LABEL, new LabelHandler(), LabelNode.class);
        addHandler(AbstractInsnNode.LDC_INSN, new LdcHandler(), LdcInsnNode.class);
        addHandler(AbstractInsnNode.IINC_INSN, new IincHandler(), IincInsnNode.class);
        addHandler(AbstractInsnNode.TABLESWITCH_INSN, new TableSwitchHandler(), TableSwitchInsnNode.class);
        addHandler(AbstractInsnNode.LOOKUPSWITCH_INSN, new LookupSwitchHandler(), LookupSwitchInsnNode.class);
        addHandler(AbstractInsnNode.MULTIANEWARRAY_INSN, new MultiANewArrayHandler(), MultiANewArrayInsnNode.class);
        addHandler(AbstractInsnNode.FRAME, new FrameHandler(), FrameNode.class);
        addHandler(AbstractInsnNode.LINE, new LineNumberHandler(), LineNumberNode.class);
    }

    private <T extends AbstractInsnNode> void addHandler(int id, InstructionTypeHandler<T> handler, Class<T> instructionClass) {
        handlers[id] = new InstructionHandlerContainer<>(handler, instructionClass);
    }

    public static boolean shouldProcess(MethodNode method) {
        return shouldProcess(method, CodegenMode.LEGACY);
    }

    public static boolean shouldProcess(MethodNode method, CodegenMode codegenMode) {
        return !Util.getFlag(method.access, Opcodes.ACC_ABSTRACT) &&
                !Util.getFlag(method.access, Opcodes.ACC_NATIVE) &&
                (!method.name.equals("<init>") || codegenMode == CodegenMode.IR);
    }

    public static String getClassGetter(MethodContext context, String desc) {
        if (desc.startsWith("[")) {
            return "env->FindClass(" + context.getStringPool().get(desc) + ")";
        }
        if (desc.endsWith(";")) {
            desc = desc.substring(1, desc.length() - 1);
        }
        return "utils::find_class_wo_static(env, classloader, " + context.getCachedStrings().getPointer(desc.replace('/', '.')) + ")";
    }

    public void processMethod(MethodContext context) {
        MethodNode method = context.method;
        MethodShellEmitter.Shell shell = shellEmitter.beginLegacy(context);

        for (int instruction = 0; instruction < method.instructions.size(); ++instruction) {
            AbstractInsnNode node = method.instructions.get(instruction);
            context.output.append("    // ").append(Util.escapeCommentString(handlers[node.getType()]
                    .insnToString(context, node))).append("; Stack: ").append(context.stackPointer).append("\n");
            handlers[node.getType()].accept(context, node);
            context.stackPointer = handlers[node.getType()].getNewStackPointer(node, context.stackPointer);
            context.output.append("    // New stack: ").append(context.stackPointer).append("\n");
        }
        shellEmitter.finishLegacy(context, shell);
    }

    public static String nameFromNode(MethodNode m, ClassNode cn) {
        return cn.name + '#' + m.name + '!' + m.desc;
    }

}
