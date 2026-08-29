package by.radioegor146;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class HiddenMethodsPool {

    private final String baseName;

    public HiddenMethodsPool(String baseName) {
        this.baseName = baseName;
    }

    private final HashMap<String, Integer> namePool = new HashMap<>();
    private final HashMap<String, HashMap<String, HiddenMethod>> methods = new HashMap<>();
    private final List<ClassNode> classes = new ArrayList<>();
    private final Map<ClassNode, HashMap<String, HiddenMethod>> ownerMethods =
            new IdentityHashMap<>();

    public static class HiddenMethod {

        private final ClassNode classNode;
        private final MethodNode methodNode;

        private HiddenMethod(ClassNode classNode, MethodNode methodNode) {
            this.classNode = classNode;
            this.methodNode = methodNode;
        }

        public ClassNode getClassNode() {
            return classNode;
        }

        public MethodNode getMethodNode() {
            return methodNode;
        }
    }

    public HiddenMethod getMethod(String name, String desc, Consumer<MethodNode> creator) {
        return getMethod(name, desc, desc, creator);
    }

    public HiddenMethod getMethod(String name, String desc, String cacheKey,
                                  Consumer<MethodNode> creator) {
        HiddenMethod existingMethod = methods.computeIfAbsent(name, unused -> new HashMap<>())
                .get(cacheKey);
        if (existingMethod != null) {
            return existingMethod;
        }

        String newName = name + namePool.compute(name, (otherName, value) -> value == null ? 0 : value + 1);
        MethodNode newMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE |
                Opcodes.ACC_SYNTHETIC, newName, desc, null, new String[0]);
        creator.accept(newMethod);
        ClassNode classNode = classes.isEmpty() ? null : classes.get(classes.size() - 1).methods.size() > 10000 ? null : classes.get(classes.size() - 1);
        if (classNode == null) {
            classNode = new ClassNode(Opcodes.ASM9);
            classNode.access = Opcodes.ACC_PUBLIC;
            classNode.version = 52;
            classNode.name = baseName + "/Hidden" + classes.size();
            classNode.superName = Type.getInternalName(Object.class);
            classes.add(classNode);
        }
        classNode.methods.add(newMethod);
        HiddenMethod hiddenMethod = new HiddenMethod(classNode, newMethod);
        methods.computeIfAbsent(name, unused -> new HashMap<>()).put(cacheKey, hiddenMethod);
        return hiddenMethod;
    }

    public HiddenMethod getMethod(ClassNode owner, String name, String desc,
                                  Consumer<MethodNode> creator) {
        String cacheKey = name + '\0' + desc;
        HiddenMethod existingMethod = ownerMethods
                .computeIfAbsent(owner, unused -> new HashMap<>()).get(cacheKey);
        if (existingMethod != null) {
            return existingMethod;
        }

        String newName;
        do {
            newName = name + namePool.compute(
                    name, (otherName, value) -> value == null ? 0 : value + 1);
        } while (hasMethodNamed(owner, newName));

        MethodNode newMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                newName, desc, null, new String[0]);
        creator.accept(newMethod);
        owner.methods.add(newMethod);
        HiddenMethod hiddenMethod = new HiddenMethod(owner, newMethod);
        ownerMethods.computeIfAbsent(owner, unused -> new HashMap<>())
                .put(cacheKey, hiddenMethod);
        return hiddenMethod;
    }

    private static boolean hasMethodNamed(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) {
            if (method.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public List<ClassNode> getClasses() {
        return classes;
    }
}
