package by.radioegor146;

import by.radioegor146.bytecode.PreprocessorRunner;
import by.radioegor146.interpreter.InterpreterMethodEmitter;
import by.radioegor146.interpreter.InterpreterMethodProcessor;
import by.radioegor146.ir.IrMethodCompiler;
import by.radioegor146.ir.UnsupportedIrConstructException;
import by.radioegor146.ir.emit.MethodShellEmitter;
import by.radioegor146.ir.frontend.JsrRetInliner;
import by.radioegor146.source.CMakeFilesBuilder;
import by.radioegor146.source.ClassSourceBuilder;
import by.radioegor146.source.MainSourceBuilder;
import by.radioegor146.source.StringPool;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.gravit.launchserver.asm.ClassMetadataReader;
import ru.gravit.launchserver.asm.SafeClassWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class NativeObfuscator {

    private static final Logger logger = LoggerFactory.getLogger(NativeObfuscator.class);
    private static final List<String> SDK_CLASS_NAMES = Arrays.asList(
            "by/radioegor146/nativeobfuscator/NativePrimitives",
            "by/radioegor146/nativeobfuscator/NativeStrings",
            "by/radioegor146/sdk/NativePrimitives",
            "by/radioegor146/sdk/NativeStrings");

    private final StringPool stringPool;
    private final InterpreterMethodProcessor interpreterMethodProcessor;
    private IrMethodCompiler irMethodCompiler;
    private NativeIntrinsicsMode intrinsicsMode = NativeIntrinsicsMode.SAFE;

    private final NodeCache<String> cachedStrings;
    private final NodeCache<String> cachedClasses;
    private final NodeCache<CachedMethodInfo> cachedMethods;
    private final NodeCache<CachedFieldInfo> cachedFields;

    public static class InvokeDynamicInfo {
        private final String methodName;
        private final int index;

        public InvokeDynamicInfo(String methodName, int index) {
            this.methodName = methodName;
            this.index = index;
        }

        public String getMethodName() {
            return methodName;
        }

        public int getIndex() {
            return index;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InvokeDynamicInfo that = (InvokeDynamicInfo) o;
            return index == that.index && Objects.equals(methodName, that.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodName, index);
        }
    }

    private HiddenMethodsPool hiddenMethodsPool;

    private int currentClassId;
    private String nativeDir;

    public NativeObfuscator() {
        stringPool = new StringPool();
        cachedStrings = new NodeCache<>("(cstrings[%d])");
        cachedClasses = new NodeCache<>("(cclasses[%d])");
        cachedMethods = new NodeCache<>("(cmethods[%d].load(std::memory_order_acquire))");
        cachedFields = new NodeCache<>("(cfields[%d].load(std::memory_order_acquire))");
        MethodShellEmitter shellEmitter = new MethodShellEmitter(this);
        interpreterMethodProcessor = new InterpreterMethodProcessor();
        irMethodCompiler = new IrMethodCompiler(shellEmitter);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                CodegenMode.IR, CompilerBackend.CPP, IrLoweringMode.DIRECT);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, CompilerBackend.CPP, IrLoweringMode.DIRECT);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, CompilerBackend backend) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, backend, IrLoweringMode.DIRECT);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, IrLoweringMode irLoweringMode)
            throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, CompilerBackend.CPP, irLoweringMode);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, CompilerBackend backend,
                          IrLoweringMode irLoweringMode) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, backend, irLoweringMode, NativeIntrinsicsMode.SAFE);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, CompilerBackend backend,
                          IrLoweringMode irLoweringMode,
                          NativeIntrinsicsMode intrinsicsMode) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, backend, irLoweringMode, intrinsicsMode,
                ControlFlowObfuscationMode.OFF);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, CompilerBackend backend,
                          IrLoweringMode irLoweringMode,
                          NativeIntrinsicsMode intrinsicsMode,
                          ControlFlowObfuscationMode cfObfuscation) throws IOException {
        return process(inputJarPath, outputDir, inputLibs, blackList, whiteList, plainLibName,
                customLibraryDirectory, platform, useAnnotations, generateDebugJar,
                codegenMode, backend, irLoweringMode, intrinsicsMode, cfObfuscation,
                DirectNativeCallMode.OFF);
    }

    public String process(Path inputJarPath, Path outputDir, List<Path> inputLibs,
                          List<String> blackList, List<String> whiteList, String plainLibName,
                          String customLibraryDirectory,
                          Platform platform, boolean useAnnotations, boolean generateDebugJar,
                          CodegenMode codegenMode, CompilerBackend backend,
                          IrLoweringMode irLoweringMode,
                          NativeIntrinsicsMode intrinsicsMode,
                          ControlFlowObfuscationMode cfObfuscation,
                          DirectNativeCallMode directNativeCall) throws IOException {
        Objects.requireNonNull(codegenMode, "codegenMode");
        final CompilerBackend selectedBackend = Objects.requireNonNull(backend, "backend");
        final IrLoweringMode selectedIrLowering =
                Objects.requireNonNull(irLoweringMode, "irLoweringMode");
        final ControlFlowObfuscationMode selectedCfObfuscation =
                Objects.requireNonNull(cfObfuscation, "cfObfuscation");
        final DirectNativeCallMode selectedDirectNative =
                Objects.requireNonNull(directNativeCall, "directNativeCall");
        this.intrinsicsMode = Objects.requireNonNull(intrinsicsMode, "intrinsicsMode");
        this.irMethodCompiler = new IrMethodCompiler(
                new MethodShellEmitter(this), this.intrinsicsMode);
        final boolean[] evaluatorRuntimeCopied = {
                selectedIrLowering == IrLoweringMode.EVAL};
        final boolean[] interpreterRuntimeCopied = {
                selectedBackend == CompilerBackend.INTERPRETER};
        if (Files.exists(outputDir) && Files.isSameFile(inputJarPath.toRealPath().getParent(), outputDir.toRealPath())) {
            throw new RuntimeException("Input jar can't be in the same directory as output directory");
        }

        List<Path> libs = new ArrayList<>(inputLibs);
        libs.add(inputJarPath);
        ClassMethodFilter classMethodFilter = new ClassMethodFilter(ClassMethodList.parse(blackList), ClassMethodList.parse(whiteList), useAnnotations);
        ClassMetadataReader metadataReader = new ClassMetadataReader(libs.stream().map(x -> {
            try {
                return new JarFile(x.toFile());
            } catch (IOException ex) {
                return null;
            }
        }).collect(Collectors.toList()));

        Path cppDir = outputDir.resolve("cpp");
        Path cppOutput = cppDir.resolve("output");
        Files.createDirectories(cppOutput);

        Util.copyResource("sources/native_jvm.cpp", cppDir);
        Util.copyResource("sources/native_jvm.hpp", cppDir);
        Util.copyResource("sources/native_jvm_output.hpp", cppDir);
        Util.copyResource("sources/string_pool.hpp", cppDir);
        if (evaluatorRuntimeCopied[0]) {
            copyEvaluatorRuntime(cppDir);
        }
        if (interpreterRuntimeCopied[0]) {
            copyInterpreterRuntime(cppDir);
        }

        Path sdkDir = cppDir.resolve("sdk");
        Path sdkThirdPartyDir = sdkDir.resolve("third_party");
        Path sha256Dir = sdkThirdPartyDir.resolve("sha-2");
        Path tinyAesDir = sdkThirdPartyDir.resolve("tiny-aes-c");
        Files.createDirectories(sha256Dir);
        Files.createDirectories(tinyAesDir);
        Util.copyResource("sources/sdk/c_api.h", sdkDir);
        Util.copyResource("sources/sdk/aes_gcm.hpp", sdkDir);
        Util.copyResource("sources/sdk/aes_gcm.cpp", sdkDir);
        Util.copyResource("sources/sdk/native_primitives.hpp", sdkDir);
        Util.copyResource("sources/sdk/native_primitives.cpp", sdkDir);
        Util.copyResource("sources/sdk/native_strings.hpp", sdkDir);
        Util.copyResource("sources/sdk/native_strings.cpp", sdkDir);
        Util.copyResource("sources/sdk/third_party/README.md", sdkThirdPartyDir);
        Util.copyResource("sources/sdk/third_party/sha-2/sha-256.h", sha256Dir);
        Util.copyResource("sources/sdk/third_party/sha-2/sha-256.cpp", sha256Dir);
        Util.copyResource("sources/sdk/third_party/sha-2/LICENSE.md", sha256Dir);
        Util.copyResource("sources/sdk/third_party/tiny-aes-c/aes.h", tinyAesDir);
        Util.copyResource("sources/sdk/third_party/tiny-aes-c/aes.cpp", tinyAesDir);
        Util.copyResource(
                "sources/sdk/third_party/tiny-aes-c/UNLICENSE.txt",
                tinyAesDir);

        String projectName = "native_library";

        CMakeFilesBuilder cMakeBuilder = new CMakeFilesBuilder(projectName);
        cMakeBuilder.addMainFile("native_jvm.hpp");
        cMakeBuilder.addMainFile("native_jvm.cpp");
        cMakeBuilder.addMainFile("native_jvm_output.hpp");
        cMakeBuilder.addMainFile("native_jvm_output.cpp");
        cMakeBuilder.addMainFile("string_pool.hpp");
        cMakeBuilder.addMainFile("string_pool.cpp");
        cMakeBuilder.addMainFile("sdk/c_api.h");
        cMakeBuilder.addMainFile("sdk/aes_gcm.hpp");
        cMakeBuilder.addMainFile("sdk/aes_gcm.cpp");
        cMakeBuilder.addMainFile("sdk/native_primitives.hpp");
        cMakeBuilder.addMainFile("sdk/native_primitives.cpp");
        cMakeBuilder.addMainFile("sdk/native_strings.hpp");
        cMakeBuilder.addMainFile("sdk/native_strings.cpp");
        cMakeBuilder.addMainFile("sdk/third_party/sha-2/sha-256.h");
        cMakeBuilder.addMainFile("sdk/third_party/sha-2/sha-256.cpp");
        cMakeBuilder.addMainFile("sdk/third_party/tiny-aes-c/aes.h");
        cMakeBuilder.addMainFile("sdk/third_party/tiny-aes-c/aes.cpp");
        if (evaluatorRuntimeCopied[0]) {
            addEvaluatorRuntimeToCmake(cMakeBuilder);
        }
        if (interpreterRuntimeCopied[0]) {
            addInterpreterRuntimeToCmake(cMakeBuilder);
        }

        if (platform == Platform.HOTSPOT) {
            cMakeBuilder.addFlag("USE_HOTSPOT");
        }

        MainSourceBuilder mainSourceBuilder = new MainSourceBuilder();

        File jarFile = inputJarPath.toAbsolutePath().toFile();
        try (JarFile jar = new JarFile(jarFile);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outputDir.resolve(jarFile.getName())));
             ZipOutputStream debug = generateDebugJar ? new ZipOutputStream(
                     Files.newOutputStream(outputDir.resolve("debug.jar"))) : null) {

            logger.info("Processing {}...", jarFile);

            if (customLibraryDirectory != null) {
                nativeDir = customLibraryDirectory;

                if (jar.stream().anyMatch(x -> x.getName().equals(nativeDir) ||
                                               x.getName().startsWith(nativeDir + "/"))) {
                    logger.warn("Directory '{}' already exists in input jar file", nativeDir);
                }
            } else {
                int nativeDirId = IntStream.iterate(0, i -> i + 1)
                        .filter(i -> jar.stream().noneMatch(x -> x.getName().equals("native" + i) ||
                                                                 x.getName().startsWith("native" + i + "/")))
                        .findFirst().orElseThrow(RuntimeException::new);
                nativeDir = "native" + nativeDirId;
            }

            hiddenMethodsPool = new HiddenMethodsPool(nativeDir + "/hidden");

            Integer[] classIndexReference = new Integer[]{0};

            jar.stream().forEach(entry -> {
                if (entry.getName().equals(JarFile.MANIFEST_NAME)) return;

                try {
                    if (SDK_CLASS_NAMES.stream().anyMatch(
                            name -> entry.getName().equals(name + ".class"))) {
                        if (debug != null) {
                            Util.writeEntry(jar, debug, entry);
                        }
                        return;
                    }

                    if (!entry.getName().endsWith(".class")) {
                        Util.writeEntry(jar, out, entry);
                        if (debug != null) {
                            Util.writeEntry(jar, debug, entry);
                        }
                        return;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream in = jar.getInputStream(entry)) {
                        Util.transfer(in, baos);
                    }
                    byte[] src = baos.toByteArray();

                    if (Util.byteArrayToInt(Arrays.copyOfRange(src, 0, 4)) != 0xCAFEBABE) {
                        Util.writeEntry(out, entry.getName(), src);
                        if (debug != null) {
                            Util.writeEntry(debug, entry.getName(), src);
                        }
                        return;
                    }

                    StringBuilder nativeMethods = new StringBuilder();
                    List<HiddenCppMethod> hiddenMethods = new ArrayList<>();

                    ClassReader classReader = new ClassReader(src);
                    ClassNode rawClassNode = new ClassNode(Opcodes.ASM9);
                    classReader.accept(rawClassNode, 0);

                    if (!classMethodFilter.shouldProcess(rawClassNode) ||
                        rawClassNode.methods.stream().noneMatch(method ->
                                MethodProcessor.shouldProcess(method) &&
                                classMethodFilter.shouldProcess(rawClassNode, method))) {
                        logger.info("Skipping {}", rawClassNode.name);
                        if (useAnnotations) {
                            ClassMethodFilter.cleanAnnotations(rawClassNode);
                            ClassWriter clearedClassWriter = new SafeClassWriter(metadataReader, 0);
                            rawClassNode.accept(clearedClassWriter);
                            Util.writeEntry(out, entry.getName(), clearedClassWriter.toByteArray());
                            if (debug != null) {
                                Util.writeEntry(debug, entry.getName(), clearedClassWriter.toByteArray());
                            }
                            return;
                        }
                        Util.writeEntry(out, entry.getName(), src);
                        if (debug != null) {
                            Util.writeEntry(debug, entry.getName(), src);
                        }
                        return;
                    }

                    logger.info("Preprocessing {}", rawClassNode.name);

                    List<MethodNode> methodsToPreprocess =
                            rawClassNode.methods.stream()
                            .filter(MethodProcessor::shouldProcess)
                            .filter(methodNode -> classMethodFilter.shouldProcess(rawClassNode, methodNode))
                            .collect(Collectors.toList());
                    List<MethodNode> preparedMethods =
                            new ArrayList<>(methodsToPreprocess.size());
                    for (MethodNode method : methodsToPreprocess) {
                        preparedMethods.add(
                                prepareForIrLeavingOriginal(
                                        rawClassNode, method));
                    }
                    for (int i = 0; i < methodsToPreprocess.size(); i++) {
                        MethodNode prepared = preparedMethods.get(i);
                        if (prepared != methodsToPreprocess.get(i)) {
                            JsrRetInliner.installCode(
                                    methodsToPreprocess.get(i), prepared);
                        }
                    }
                    methodsToPreprocess.forEach(methodNode ->
                            PreprocessorRunner.preprocess(
                                    rawClassNode, methodNode, platform));

                    ClassWriter preprocessorClassWriter = new SafeClassWriter(
                            metadataReader, classWriterFlags(rawClassNode));
                    rawClassNode.accept(preprocessorClassWriter);
                    if (debug != null) {
                        Util.writeEntry(debug, entry.getName(), preprocessorClassWriter.toByteArray());
                    }
                    classReader = new ClassReader(preprocessorClassWriter.toByteArray());
                    ClassNode classNode = new ClassNode(Opcodes.ASM9);
                    classReader.accept(classNode, 0);

                    logger.info("Processing {}", classNode.name);

                    if (classNode.methods.stream().noneMatch(x -> x.name.equals("<clinit>"))) {
                        classNode.methods.add(new MethodNode(Opcodes.ASM9, Opcodes.ACC_STATIC,
                                "<clinit>", "()V", null, new String[0]));
                    }

                    cachedStrings.clear();
                    cachedClasses.clear();
                    cachedMethods.clear();
                    cachedFields.clear();

                    boolean classUsesInterpreter = false;
                    for (MethodNode method : classNode.methods) {
                        if (!MethodProcessor.shouldProcess(method)
                                || !classMethodFilter.shouldProcess(classNode, method)) {
                            continue;
                        }
                        NativeAnnotationSupport.Options preview =
                                NativeAnnotationSupport.resolve(
                                        classNode, method, selectedIrLowering,
                                        this.intrinsicsMode, selectedBackend,
                                        selectedCfObfuscation, selectedDirectNative);
                        if (preview.getLowering() == IrLoweringMode.EVAL
                                && !evaluatorRuntimeCopied[0]) {
                            copyEvaluatorRuntime(cppDir);
                            addEvaluatorRuntimeToCmake(cMakeBuilder);
                            evaluatorRuntimeCopied[0] = true;
                        }
                        if (preview.getBackend() == CompilerBackend.INTERPRETER) {
                            classUsesInterpreter = true;
                            if (!interpreterRuntimeCopied[0]) {
                                copyInterpreterRuntime(cppDir);
                                addInterpreterRuntimeToCmake(cMakeBuilder);
                                interpreterRuntimeCopied[0] = true;
                            }
                        }
                    }

                    try (ClassSourceBuilder cppBuilder =
                                 new ClassSourceBuilder(cppOutput, classNode.name,
                                         classIndexReference[0]++, stringPool,
                                         classUsesInterpreter)) {
                        StringBuilder instructions = new StringBuilder();
                        Map<String, String> sameClassDirectNativeNames =
                                collectSameClassDirectNativeNames(
                                        classNode, classMethodFilter, selectedIrLowering,
                                        selectedBackend, selectedCfObfuscation,
                                        selectedDirectNative);
                        if (!sameClassDirectNativeNames.isEmpty()) {
                            for (int i = 0; i < classNode.methods.size(); i++) {
                                MethodNode method = classNode.methods.get(i);
                                if (!sameClassDirectNativeNames.containsKey(
                                        method.name + method.desc)) {
                                    continue;
                                }
                                instructions.append("    ")
                                        .append(MethodShellEmitter.jniFunctionPrototype(
                                                method, i))
                                        .append(";\n");
                            }
                            instructions.append('\n');
                        }

                        int methodsToProcess = classNode.methods.size();
                        for (int i = 0; i < methodsToProcess; i++) {
                            MethodNode method = classNode.methods.get(i);

                            if (!MethodProcessor.shouldProcess(method)) {
                                continue;
                            }

                            if (!classMethodFilter.shouldProcess(classNode, method)) {
                                continue;
                            }

                            NativeAnnotationSupport.Options options =
                                    NativeAnnotationSupport.resolve(
                                            classNode, method, selectedIrLowering,
                                            this.intrinsicsMode, selectedBackend,
                                            selectedCfObfuscation, selectedDirectNative);
                            MethodContext context = new MethodContext(this, method, i, classNode, currentClassId);
                            context.directNativeCall = options.getDirectNative();
                            context.sameClassDirectNativeNames = sameClassDirectNativeNames;
                            InterpreterMethodEmitter.CompiledMethod interpreted =
                                    options.getBackend() == CompilerBackend.INTERPRETER
                                            ? InterpreterMethodEmitter.tryCompile(classNode, method)
                                            : null;
                            if (interpreted != null) {
                                interpreterMethodProcessor.processMethod(context, interpreted);
                            } else {
                                try {
                                    irMethodCompiler.processMethod(
                                            context, options.getLowering(),
                                            options.getIntrinsics(),
                                            options.getBackend()
                                                    == CompilerBackend.INTERPRETER
                                                    ? ControlFlowObfuscationMode.OFF
                                                    : options.getCfObfuscation());
                                } catch (UnsupportedIrConstructException ex) {
                                    logger.info("IR codegen unsupported for {}#{}{}: {}; "
                                                    + "leaving method bytecode unchanged",
                                            classNode.name, method.name, method.desc,
                                            ex.getMessage());
                                    MethodNode original = readOriginalMethod(
                                            src, method.name, method.desc);
                                    classNode.methods.set(i,
                                            prepareForIrLeavingOriginal(
                                                    classNode, original));
                                    continue;
                                }
                            }
                            instructions.append(context.output.toString().replace("\n", "\n    "));

                            nativeMethods.append(context.nativeMethods);

                            if (context.proxyMethod != null) {
                                hiddenMethods.add(new HiddenCppMethod(context.proxyMethod, context.cppNativeMethodName));
                            }

                            if ((classNode.access & Opcodes.ACC_INTERFACE) > 0) {
                                method.access &= ~Opcodes.ACC_NATIVE;
                            }
                        }

                        if (useAnnotations) {
                            ClassMethodFilter.cleanAnnotations(classNode);
                        }

                        // Raise legacy classes to Java 8 (52), because injected code needs it
                        // (interface method bodies require 52, class-literal LDC requires 49).
                        // Never downgrade: stamping e.g. a Java 17 (61) class as 52 makes the JVM
                        // ignore its NestHost/NestMembers, Record and PermittedSubclasses
                        // attributes even though ASM still writes them.
                        if (containsJsrOrRet(classNode)) {
                            if ((classNode.version & 0xFFFF) < Opcodes.V1_5) {
                                classNode.version = Opcodes.V1_5;
                            }
                        } else if ((classNode.version & 0xFFFF) < Opcodes.V1_8) {
                            classNode.version = Opcodes.V1_8;
                        }
                        ClassWriter classWriter = new SafeClassWriter(
                                metadataReader, classWriterFlags(classNode));
                        classNode.accept(classWriter);
                        Util.writeEntry(out, entry.getName(), classWriter.toByteArray());

                        cppBuilder.addHeader(cachedStrings.size(), cachedClasses.size(), cachedMethods.size(), cachedFields.size());
                        cppBuilder.addInstructions(instructions.toString());
                        cppBuilder.registerMethods(cachedStrings, cachedClasses, nativeMethods.toString(), hiddenMethods);

                        cMakeBuilder.addClassFile("output/" + cppBuilder.getHppFilename());
                        cMakeBuilder.addClassFile("output/" + cppBuilder.getCppFilename());

                        mainSourceBuilder.addHeader(cppBuilder.getHppFilename());
                        mainSourceBuilder.registerClassMethods(currentClassId, cppBuilder.getFilename());
                    }

                    currentClassId++;
                } catch (IOException ex) {
                    logger.error("Error while processing {}", entry.getName(), ex);
                }
            });

            // Hidden helpers are ordinary symbolic call targets from the generated native
            // code, so every helper remains in the output jar. Generic trampolines also keep
            // the existing eager DefineClass path. Application-dependent condy companions
            // must instead be loaded from the jar by the transformed class's loader.
            for (ClassNode hiddenClass : hiddenMethodsPool.getClasses()) {
                ClassWriter classWriter = new SafeClassWriter(metadataReader,
                        ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                hiddenClass.accept(classWriter);
                byte[] rawData = classWriter.toByteArray();
                Util.writeEntry(out, hiddenClass.name + ".class", rawData);
                if (debug != null) {
                    Util.writeEntry(debug, hiddenClass.name + ".class", rawData);
                }
            }
            if (platform != Platform.ANDROID) {
                for (ClassNode hiddenClass : hiddenMethodsPool.getClasses()) {
                    if (hiddenMethodsPool.isCompanionClass(hiddenClass)) {
                        continue;
                    }
                    String hiddenClassFileName = "data_" + Util.escapeCppNameString(hiddenClass.name.replace('/', '_'));

                    cMakeBuilder.addClassFile("output/" + hiddenClassFileName + ".hpp");
                    cMakeBuilder.addClassFile("output/" + hiddenClassFileName + ".cpp");

                    mainSourceBuilder.addHeader(hiddenClassFileName + ".hpp");
                    mainSourceBuilder.registerDefine(stringPool.get(hiddenClass.name), hiddenClassFileName);

                    ClassWriter classWriter = new SafeClassWriter(metadataReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    hiddenClass.accept(classWriter);
                    byte[] rawData = classWriter.toByteArray();
                    List<Byte> data = new ArrayList<>(rawData.length);
                    for (byte b : rawData) {
                        data.add(b);
                    }

                    try (BufferedWriter hppWriter = Files.newBufferedWriter(cppOutput.resolve(hiddenClassFileName + ".hpp"))) {
                        hppWriter.append("#include \"../native_jvm.hpp\"\n\n");
                        hppWriter.append("#ifndef ").append(hiddenClassFileName.toUpperCase()).append("_HPP_GUARD\n\n");
                        hppWriter.append("#define ").append(hiddenClassFileName.toUpperCase()).append("_HPP_GUARD\n\n");
                        hppWriter.append("namespace native_jvm::data::__ngen_").append(hiddenClassFileName).append(" {\n");
                        hppWriter.append("    const jbyte* get_class_data();\n");
                        hppWriter.append("    const jsize get_class_data_length();\n");
                        hppWriter.append("}\n\n");
                        hppWriter.append("#endif\n");
                    }

                    try (BufferedWriter cppWriter = Files.newBufferedWriter(cppOutput.resolve(hiddenClassFileName + ".cpp"))) {
                        cppWriter.append("#include \"").append(hiddenClassFileName).append(".hpp\"\n\n");
                        cppWriter.append("namespace native_jvm::data::__ngen_").append(hiddenClassFileName).append(" {\n");
                        cppWriter.append("    static const jbyte class_data[").append(String.valueOf(data.size())).append("] = { ");
                        cppWriter.append(data.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                        cppWriter.append("};\n");
                        cppWriter.append("    static const jsize class_data_length = ").append(String.valueOf(data.size())).append(";\n\n");
                        cppWriter.append("    const jbyte* get_class_data() { return class_data; }\n");
                        cppWriter.append("    const jsize get_class_data_length() { return class_data_length; }\n");
                        cppWriter.append("}\n");
                    }
                }
            }

            String loaderClassName = nativeDir + "/Loader";

            ClassNode loaderClass;

            if (plainLibName == null) {
                ClassReader loaderClassReader = new ClassReader(Objects.requireNonNull(NativeObfuscator.class
                        .getResourceAsStream("compiletime/LoaderUnpack.class")));
                loaderClass = new ClassNode(Opcodes.ASM9);
                loaderClassReader.accept(loaderClass, 0);
                loaderClass.sourceFile = "synthetic";
                System.out.println("/" + nativeDir + "/");
            } else {
                ClassReader loaderClassReader = new ClassReader(Objects.requireNonNull(NativeObfuscator.class
                        .getResourceAsStream("compiletime/LoaderPlain.class")));
                loaderClass = new ClassNode(Opcodes.ASM9);
                loaderClassReader.accept(loaderClass, 0);
                loaderClass.sourceFile = "synthetic";
                loaderClass.methods.forEach(method -> {
                    for (int i = 0; i < method.instructions.size(); i++) {
                        AbstractInsnNode insnNode = method.instructions.get(i);
                        if (insnNode instanceof LdcInsnNode && ((LdcInsnNode) insnNode).cst instanceof String &&
                            ((LdcInsnNode) insnNode).cst.equals("%LIB_NAME%")) {
                            ((LdcInsnNode) insnNode).cst = plainLibName;
                        }
                    }
                });
            }

            ClassNode resultLoaderClass = new ClassNode(Opcodes.ASM9);
            String originalLoaderClassName = loaderClass.name;
            loaderClass.accept(new ClassRemapper(resultLoaderClass, new Remapper() {
                @Override
                public String map(String internalName) {
                    return internalName.equals(originalLoaderClassName) ? loaderClassName : internalName;
                }
            }));

            ClassWriter classWriter = new SafeClassWriter(metadataReader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            resultLoaderClass.accept(classWriter);
            Util.writeEntry(out, loaderClassName + ".class", classWriter.toByteArray());
            for (String sdkClassName : SDK_CLASS_NAMES) {
                Util.writeEntry(
                        out,
                        sdkClassName + ".class",
                        buildSdkClass(sdkClassName, loaderClassName));
            }

            logger.info("Jar file ready!");
            Manifest mf = buildOutputManifest(jar.getManifest());
            out.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
            mf.write(out);
            out.closeEntry();
            metadataReader.close();
        }

        Files.write(cppDir.resolve("string_pool.cpp"), stringPool.build().getBytes(StandardCharsets.UTF_8));

        Files.write(cppDir.resolve("native_jvm_output.cpp"), mainSourceBuilder.build(nativeDir, currentClassId)
                .getBytes(StandardCharsets.UTF_8));

        Files.write(cppDir.resolve("CMakeLists.txt"), cMakeBuilder.build().getBytes(StandardCharsets.UTF_8));

        return nativeDir;
    }

    static final Attributes.Name ENABLE_NATIVE_ACCESS =
            new Attributes.Name("Enable-Native-Access");

    /**
     * Builds the manifest written to the output JAR. Generated classes call
     * {@code System.load}/{@code System.loadLibrary}, which JEP 472 restricts on
     * JDK 24+. Declaring {@code Enable-Native-Access: ALL-UNNAMED} lets a
     * {@code java -jar} launch grant native access to the unnamed module without
     * a command-line flag. Any pre-existing, more specific value from the input
     * manifest is preserved; all other input attributes (e.g. {@code Main-Class})
     * are carried through unchanged.
     */
    static Manifest buildOutputManifest(Manifest inputManifest) {
        Manifest mf = inputManifest != null ? new Manifest(inputManifest) : new Manifest();
        Attributes mainAttributes = mf.getMainAttributes();
        if (mainAttributes.getValue(Attributes.Name.MANIFEST_VERSION) == null) {
            mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }
        if (mainAttributes.getValue(ENABLE_NATIVE_ACCESS) == null) {
            mainAttributes.put(ENABLE_NATIVE_ACCESS, "ALL-UNNAMED");
        }
        return mf;
    }

    private static boolean containsJsrOrRet(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                int opcode = instruction.getOpcode();
                if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int classWriterFlags(ClassNode classNode) {
        return containsJsrOrRet(classNode)
                ? ClassWriter.COMPUTE_MAXS
                : ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES;
    }

    private MethodNode prepareForIrLeavingOriginal(ClassNode owner, MethodNode method) {
        try {
            return JsrRetInliner.prepareForIr(owner, method);
        } catch (UnsupportedIrConstructException ex) {
            logger.info("JSR/RET inlining unsupported for {}#{}{}: {}; "
                            + "leaving method bytecode unchanged",
                    owner.name, method.name, method.desc, ex.getMessage());
            return method;
        }
    }

    private static MethodNode readOriginalMethod(byte[] classBytes, String name,
                                                 String descriptor) {
        ClassNode originalClass = new ClassNode(Opcodes.ASM9);
        new ClassReader(classBytes).accept(originalClass, 0);
        for (MethodNode method : originalClass.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)) {
                return method;
            }
        }
        throw new IllegalStateException(
                "Original method not found: " + name + descriptor);
    }

    private byte[] buildSdkClass(String sdkClassName, String loaderClassName) throws IOException {
        String resourceName = sdkClassName + ".class";
        ClassNode sdkClass = new ClassNode(Opcodes.ASM9);
        try (InputStream input = NativeObfuscator.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("SDK class resource is missing: " + resourceName);
            }
            new ClassReader(input).accept(sdkClass, 0);
        }

        MethodNode classInitializer = sdkClass.methods.stream()
                .filter(method -> method.name.equals("<clinit>") && method.desc.equals("()V"))
                .findFirst()
                .orElse(null);
        MethodInsnNode loadLibrary = new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                loaderClassName,
                "load",
                "()V",
                false);
        if (classInitializer == null) {
            classInitializer = new MethodNode(
                    Opcodes.ASM9,
                    Opcodes.ACC_STATIC,
                    "<clinit>",
                    "()V",
                    null,
                    new String[0]);
            classInitializer.instructions.add(loadLibrary);
            classInitializer.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
            sdkClass.methods.add(classInitializer);
        } else {
            classInitializer.instructions.insert(loadLibrary);
        }

        sdkClass.version = Opcodes.V1_8;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        sdkClass.accept(writer);
        return writer.toByteArray();
    }

    private static void copyEvaluatorRuntime(Path cppDir) throws IOException {
        Util.copyResource("sources/native_jvm_eval.cpp", cppDir);
        Util.copyResource("sources/native_jvm_eval.hpp", cppDir);
        Files.write(cppDir.resolve("native_jvm.hpp"),
                "\n#include \"native_jvm_eval.hpp\"\n".getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.APPEND);
    }

    private static void copyInterpreterRuntime(Path cppDir) throws IOException {
        Util.copyResource("sources/native_jvm_interp.cpp", cppDir);
        Util.copyResource("sources/native_jvm_interp.hpp", cppDir);
    }

    private static void addEvaluatorRuntimeToCmake(CMakeFilesBuilder cMakeBuilder) {
        cMakeBuilder.addMainFile("native_jvm_eval.hpp");
        cMakeBuilder.addMainFile("native_jvm_eval.cpp");
    }

    private static void addInterpreterRuntimeToCmake(CMakeFilesBuilder cMakeBuilder) {
        cMakeBuilder.addMainFile("native_jvm_interp.hpp");
        cMakeBuilder.addMainFile("native_jvm_interp.cpp");
    }

    private Map<String, String> collectSameClassDirectNativeNames(
            ClassNode classNode, ClassMethodFilter filter,
            IrLoweringMode irLowering, CompilerBackend backend,
            ControlFlowObfuscationMode cfObfuscation,
            DirectNativeCallMode cliDirectNative) {
        Map<String, String> names = new LinkedHashMap<String, String>();
        if (Util.getFlag(classNode.access, Opcodes.ACC_INTERFACE)) {
            return names;
        }
        boolean anyEnabled = false;
        for (int i = 0; i < classNode.methods.size(); i++) {
            MethodNode method = classNode.methods.get(i);
            if (!MethodProcessor.shouldProcess(method)
                    || !filter.shouldProcess(classNode, method)) {
                continue;
            }
            NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                    classNode, method, irLowering, this.intrinsicsMode, backend,
                    cfObfuscation, cliDirectNative);
            if (options.getDirectNative().enabled()) {
                anyEnabled = true;
                break;
            }
        }
        if (!anyEnabled) {
            return names;
        }
        for (int i = 0; i < classNode.methods.size(); i++) {
            MethodNode method = classNode.methods.get(i);
            if (!MethodProcessor.shouldProcess(method)
                    || !filter.shouldProcess(classNode, method)) {
                continue;
            }
            NativeAnnotationSupport.Options options = NativeAnnotationSupport.resolve(
                    classNode, method, irLowering, this.intrinsicsMode, backend,
                    cfObfuscation, cliDirectNative);
            if (options.getBackend() == CompilerBackend.INTERPRETER) {
                continue;
            }
            if (!DirectNativeCallMode.calleeEligible(classNode, method)) {
                continue;
            }
            if (!irMethodCompiler.admitsIr(classNode, method, options.getIntrinsics())) {
                continue;
            }
            names.put(method.name + method.desc,
                    MethodShellEmitter.cppNativeFunctionName(method, i));
        }
        return names;
    }

    public NativeIntrinsicsMode getIntrinsicsMode() {
        return intrinsicsMode;
    }

    public StringPool getStringPool() {
        return stringPool;
    }

    public NodeCache<String> getCachedStrings() {
        return cachedStrings;
    }

    public NodeCache<String> getCachedClasses() {
        return cachedClasses;
    }

    public NodeCache<CachedMethodInfo> getCachedMethods() {
        return cachedMethods;
    }

    public NodeCache<CachedFieldInfo> getCachedFields() {
        return cachedFields;
    }

    public String getNativeDir() {
        return nativeDir;
    }

    public HiddenMethodsPool getHiddenMethodsPool() {
        if (hiddenMethodsPool == null) {
            hiddenMethodsPool = new HiddenMethodsPool(
                    (nativeDir == null ? "native0" : nativeDir) + "/hidden");
        }
        return hiddenMethodsPool;
    }
}
