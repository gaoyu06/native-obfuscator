package by.radioegor146.zig;

import by.radioegor146.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cross-compiles the cpp/ tree produced by {@link by.radioegor146.NativeObfuscator}
 * with {@code zig c++}, and either injects the resulting shared library into the
 * output jar (LoaderUnpack mode) or writes it next to the jar (LoaderPlain mode).
 */
public class ZigBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ZigBuilder.class);

    public static class BuildRequest {
        public Path zigExe;
        public Path cppDir;
        public Path outputJar;
        public Path externalLibsDir;
        public String nativeDir;
        public Platform platform;
        public Path jdkInclude;
        public List<ZigTarget> targets;
    }

    public void build(BuildRequest req) throws IOException, InterruptedException {
        if (req.zigExe == null || !Files.isRegularFile(req.zigExe)) {
            throw new IOException("Zig executable not found: " + req.zigExe);
        }
        if (!Files.isDirectory(req.cppDir)) {
            throw new IOException("cpp directory missing: " + req.cppDir);
        }
        if (req.jdkInclude == null || !Files.isDirectory(req.jdkInclude)) {
            throw new IOException("JDK include directory not found: " + req.jdkInclude +
                    " (set --jdk-home or JAVA_HOME)");
        }
        if (req.targets == null || req.targets.isEmpty()) {
            throw new IOException("No zig targets specified");
        }

        List<Path> sources = collectCppSources(req.cppDir);
        if (sources.isEmpty()) {
            throw new IOException("No .cpp source files under " + req.cppDir);
        }

        Path buildDir = req.cppDir.resolve("zig-build");
        Files.createDirectories(buildDir);

        for (ZigTarget target : req.targets) {
            logger.info("Building target {} ({})", target, target.zigTriple());
            Path libFile = compileTarget(req, target, sources, buildDir);
            placeArtifact(req, target, libFile);
        }
    }

    // ------------------------------------------------------------------

    private Path compileTarget(BuildRequest req, ZigTarget target, List<Path> sources, Path buildDir)
            throws IOException, InterruptedException {
        Path shimDir = buildDir.resolve("shim-" + target);
        Files.createDirectories(shimDir);
        Files.write(shimDir.resolve("jni_md.h"),
                jniMdShim(target).getBytes(StandardCharsets.UTF_8));

        Path libFile = buildDir.resolve(target.loaderFileName());
        Files.deleteIfExists(libFile);

        List<String> cmd = new ArrayList<>();
        cmd.add(req.zigExe.toAbsolutePath().toString());
        cmd.add("c++");
        cmd.add("-target");
        cmd.add(target.zigTriple());
        cmd.add("-std=c++17");
        cmd.add("-O2");
        cmd.add("-DNDEBUG");
        cmd.add("-shared");
        cmd.add("-Wno-nullability-completeness");
        if (target.os() != ZigTarget.Os.WINDOWS) {
            cmd.add("-fPIC");
        }
        cmd.add("-I");
        cmd.add(req.jdkInclude.toAbsolutePath().toString());
        cmd.add("-I");
        cmd.add(shimDir.toAbsolutePath().toString());
        if (req.platform == Platform.HOTSPOT) {
            cmd.add("-DUSE_HOTSPOT");
        }
        cmd.add("-o");
        cmd.add(libFile.toAbsolutePath().toString());
        for (Path src : sources) {
            cmd.add(src.toAbsolutePath().toString());
        }

        logger.info("  zig c++ -target {} ({} sources)", target.zigTriple(), sources.size());
        runProcess(cmd, req.cppDir);

        if (!Files.isRegularFile(libFile)) {
            throw new IOException("zig produced no output for " + target);
        }
        return libFile;
    }

    private void placeArtifact(BuildRequest req, ZigTarget target, Path libFile) throws IOException {
        String fileName = target.loaderFileName();
        if (req.externalLibsDir != null) {
            Files.createDirectories(req.externalLibsDir);
            Path dest = req.externalLibsDir.resolve(fileName);
            Files.copy(libFile, dest, StandardCopyOption.REPLACE_EXISTING);
            logger.info("  -> {}", dest);
        } else {
            if (req.outputJar == null || !Files.isRegularFile(req.outputJar)) {
                throw new IOException("Output jar not found for injection: " + req.outputJar);
            }
            injectIntoJar(req.outputJar, req.nativeDir + "/" + fileName, libFile);
            logger.info("  -> {}!{}/{}", req.outputJar.getFileName(), req.nativeDir, fileName);
        }
    }

    private static void injectIntoJar(Path jar, String entryPath, Path source) throws IOException {
        URI uri = URI.create("jar:" + jar.toUri());
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Path inside = fs.getPath(entryPath);
            if (inside.getParent() != null) {
                Files.createDirectories(inside.getParent());
            }
            Files.copy(source, inside, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void runProcess(List<String> cmd, Path workDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                logger.info("    [zig] {}", line);
            }
        }
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("zig c++ failed with exit code " + rc);
        }
    }

    private static List<Path> collectCppSources(Path cppDir) throws IOException {
        try (Stream<Path> walk = Files.walk(cppDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".cpp"))
                    // Skip the zig-build dir itself (in case of re-runs)
                    .filter(p -> !p.toString().contains(java.io.File.separator + "zig-build" + java.io.File.separator))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static String jniMdShim(ZigTarget target) {
        if (target.os() == ZigTarget.Os.WINDOWS) {
            return "#ifndef _JAVASOFT_JNI_MD_H_\n" +
                    "#define _JAVASOFT_JNI_MD_H_\n" +
                    "#define JNIEXPORT __declspec(dllexport)\n" +
                    "#define JNIIMPORT __declspec(dllimport)\n" +
                    "#if defined(__i386__) || defined(_M_IX86)\n" +
                    "#define JNICALL __stdcall\n" +
                    "#else\n" +
                    "#define JNICALL\n" +
                    "#endif\n" +
                    "typedef long jint;\n" +
                    "typedef long long jlong;\n" +
                    "typedef signed char jbyte;\n" +
                    "#endif\n";
        }
        return "#ifndef _JAVASOFT_JNI_MD_H_\n" +
                "#define _JAVASOFT_JNI_MD_H_\n" +
                "#if (defined(__GNUC__) && __GNUC__ >= 4) || defined(__clang__)\n" +
                "#define JNIEXPORT __attribute__((visibility(\"default\")))\n" +
                "#define JNIIMPORT __attribute__((visibility(\"default\")))\n" +
                "#else\n" +
                "#define JNIEXPORT\n" +
                "#define JNIIMPORT\n" +
                "#endif\n" +
                "#define JNICALL\n" +
                "typedef int jint;\n" +
                "typedef long long jlong;\n" +
                "typedef signed char jbyte;\n" +
                "#endif\n";
    }

    // ------------------------------------------------------------------
    // Locator helpers
    // ------------------------------------------------------------------

    /** Resolve a zig executable: explicit path → installer marker → PATH. */
    public static Path locateZig(Path explicit, Path installRoot) {
        if (explicit != null && Files.isRegularFile(explicit)) {
            return explicit;
        }
        ZigInstaller installer = new ZigInstaller(installRoot != null ? installRoot : ZigInstaller.defaultInstallRoot());
        ZigInstaller.Installed installed = installer.locate();
        if (installed != null) {
            return installed.exePath;
        }
        return lookupOnPath("zig");
    }

    private static Path lookupOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] suffixes = isWindows ? new String[]{".exe", ".cmd", ".bat", ""} : new String[]{""};
        for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            for (String suf : suffixes) {
                Path candidate = Paths.get(dir, name + suf);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Resolve the JDK include directory from {@code --jdk-home} arg or JAVA_HOME. */
    public static Path resolveJdkInclude(Path explicitJdkHome) {
        Path jdkHome = explicitJdkHome;
        if (jdkHome == null) {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.isEmpty()) {
                javaHome = System.getProperty("java.home");
            }
            if (javaHome != null) {
                Path candidate = Paths.get(javaHome, "include");
                if (!Files.isDirectory(candidate)) {
                    // java.home may point to the JRE inside a JDK
                    Path parent = Paths.get(javaHome).getParent();
                    if (parent != null) {
                        candidate = parent.resolve("include");
                    }
                }
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        return jdkHome.resolve("include");
    }
}
