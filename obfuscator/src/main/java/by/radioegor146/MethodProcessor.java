package by.radioegor146;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class MethodProcessor {

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

    public static boolean shouldProcess(MethodNode method) {
        return !Util.getFlag(method.access, Opcodes.ACC_ABSTRACT) &&
                !Util.getFlag(method.access, Opcodes.ACC_NATIVE);
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

    public static String nameFromNode(MethodNode m, ClassNode cn) {
        return cn.name + '#' + m.name + '!' + m.desc;
    }

}
