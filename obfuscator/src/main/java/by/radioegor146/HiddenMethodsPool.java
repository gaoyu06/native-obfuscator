package by.radioegor146;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final Map<String, ClassNode> companionClasses = new HashMap<>();
    private final Map<String, String> companionOwnersByName = new HashMap<>();

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

    /**
     * Returns the stable output name reserved for an owner's hidden companion.
     * This method does not create or register a class, so callers can validate
     * a transformation before committing any output mutation.
     */
    public String getCompanionClassName(String ownerName) {
        if (ownerName == null || ownerName.isEmpty()) {
            throw new IllegalStateException("A hidden companion requires an owner name");
        }
        if (baseName == null || baseName.isEmpty()
                || baseName.startsWith("/") || baseName.endsWith("/")
                || baseName.contains("//") || baseName.indexOf('.') >= 0
                || baseName.indexOf(';') >= 0 || baseName.indexOf('[') >= 0) {
            throw new IllegalStateException("Invalid hidden-class base name");
        }
        String name = baseName + "/HiddenCondy$" + sha256(ownerName);
        if (name.length() > 65535) {
            throw new IllegalStateException("Hidden companion name is too long");
        }
        String existingOwner = companionOwnersByName.get(name);
        if (existingOwner != null && !existingOwner.equals(ownerName)) {
            throw new IllegalStateException(
                    "Hidden companion name is already assigned to another owner");
        }
        for (ClassNode existing : classes) {
            if (name.equals(existing.name)
                    && !existing.equals(companionClasses.get(ownerName))) {
                throw new IllegalStateException(
                        "Hidden companion name collides with an existing hidden class");
            }
        }
        return name;
    }

    /**
     * Commits the companion after the caller's frontend and backend validation
     * has succeeded.
     */
    public ClassNode getCompanionClass(String ownerName, int version) {
        ClassNode existing = companionClasses.get(ownerName);
        if (existing != null) {
            return existing;
        }
        String name = getCompanionClassName(ownerName);
        ClassNode companion = new ClassNode(Opcodes.ASM9);
        companion.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        companion.version = version;
        companion.name = name;
        companion.superName = Type.getInternalName(Object.class);
        companion.sourceFile = "synthetic";
        companionClasses.put(ownerName, companion);
        companionOwnersByName.put(name, ownerName);
        classes.add(companion);
        return companion;
    }

    public ClassNode findCompanionClass(String ownerName) {
        return companionClasses.get(ownerName);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 is unavailable", impossible);
        }
    }

    public List<ClassNode> getClasses() {
        return classes;
    }
}
