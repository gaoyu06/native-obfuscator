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
    private final HashMap<Boolean, ClassNode> currentClasses = new HashMap<>();
    private final Map<ClassNode, Boolean> eagerDefinitions = new IdentityHashMap<>();

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
        return getMethod(name, desc, cacheKey, true, creator);
    }

    public HiddenMethod getMethod(String name, String desc, String cacheKey,
                                  boolean eagerDefinition,
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
        ClassNode classNode = currentClasses.get(eagerDefinition);
        if (classNode != null && classNode.methods.size() > 10000) {
            classNode = null;
        }
        if (classNode == null) {
            classNode = new ClassNode(Opcodes.ASM9);
            classNode.access = Opcodes.ACC_PUBLIC;
            classNode.version = 52;
            classNode.name = baseName + "/Hidden" + classes.size();
            classNode.superName = Type.getInternalName(Object.class);
            classes.add(classNode);
            currentClasses.put(eagerDefinition, classNode);
            eagerDefinitions.put(classNode, eagerDefinition);
        }
        classNode.methods.add(newMethod);
        HiddenMethod hiddenMethod = new HiddenMethod(classNode, newMethod);
        methods.computeIfAbsent(name, unused -> new HashMap<>()).put(cacheKey, hiddenMethod);
        return hiddenMethod;
    }

    public List<ClassNode> getClasses() {
        return classes;
    }

    public boolean requiresEagerDefinition(ClassNode classNode) {
        return Boolean.TRUE.equals(eagerDefinitions.get(classNode));
    }
}
