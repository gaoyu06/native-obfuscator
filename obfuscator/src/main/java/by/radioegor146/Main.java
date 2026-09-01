package by.radioegor146;

import by.radioegor146.zig.ZigBuilder;
import by.radioegor146.zig.ZigInstaller;
import by.radioegor146.zig.ZigTarget;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class Main {

    private static final String VERSION = "3.5.4r-zig";

    @CommandLine.Command(
            name = "native-obfuscator",
            mixinStandardHelpOptions = true,
            version = "native-obfuscator " + VERSION,
            description = "Transpiles .jar file into .cpp files and generates output .jar file",
            subcommands = {InstallZigCommand.class, CommandLine.HelpCommand.class})
    static class NativeObfuscatorRunner implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "Jar file to transpile", arity = "0..1")
        private File jarFile;

        @CommandLine.Parameters(index = "1", description = "Output directory", arity = "0..1")
        private String outputDirectory;

        @CommandLine.Option(names = {"-l", "--libraries"}, description = "Directory for dependent libraries")
        private File librariesDirectory;

        @CommandLine.Option(names = {"-b", "--black-list"}, description = "File with a list of blacklist classes/methods for transpilation")
        private File blackListFile;

        @CommandLine.Option(names = {"-w", "--white-list"}, description = "File with a list of whitelist classes/methods for transpilation")
        private File whiteListFile;

        @CommandLine.Option(names = {"--plain-lib-name"}, description = "Plain library name for LoaderPlain")
        private String libraryName;

        @CommandLine.Option(names = {"--custom-lib-dir"}, description = "Custom library directory for LoaderUnpack")
        private String customLibraryDirectory;

        @CommandLine.Option(names = {"-p", "--platform"}, defaultValue = "hotspot",
                description = "Target platform: hotspot - standard standalone HotSpot JRE, std_java - java standard, android - for Android builds (w/o DefineClass)")
        private Platform platform;

        @CommandLine.Option(names = {"--codegen"}, defaultValue = "ir",
                description = "Method code generator: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        private CodegenMode codegenMode;

        @CommandLine.Option(names = {"--ir-lower"}, defaultValue = "direct",
                description = "IR lowering strategy: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        private IrLoweringMode irLoweringMode;

        @CommandLine.Option(names = {"--native-intrinsics"}, defaultValue = "safe",
                description = "Replace selected JDK calls with native helpers: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        private NativeIntrinsicsMode nativeIntrinsicsMode;

        @CommandLine.Option(names = {"--backend"}, defaultValue = "cpp",
                description = "Compiler backend: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        private CompilerBackend backend = CompilerBackend.CPP;

        @CommandLine.Option(names = {"--ir-cf-obf"}, defaultValue = "off",
                description = "IR control-flow obfuscation: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
        private ControlFlowObfuscationMode cfObfuscation = ControlFlowObfuscationMode.OFF;

        @CommandLine.Option(names = {"-a", "--annotations"}, description = "Use annotations to ignore/include native obfuscation")
        private boolean useAnnotations;

        @CommandLine.Option(names = {"--debug"}, description = "Enable generation of debug .jar file (non-executable)")
        private boolean generateDebugJar;

        @CommandLine.Option(names = {"--use-zig"},
                description = "Compile the generated cpp/ tree with Zig and pack the resulting shared libraries into the output jar")
        private boolean useZig;

        @CommandLine.Option(names = {"--zig-targets"}, split = ",",
                description = "Comma-separated list of zig build targets (e.g. x64-windows,x64-linux,arm64-linux). " +
                        "Defaults to 'host'. Known targets: ${COMPLETION-CANDIDATES}")
        private List<String> zigTargets;

        @CommandLine.Option(names = {"--zig-path"}, description = "Path to zig executable (overrides installed/PATH)")
        private File zigPath;

        @CommandLine.Option(names = {"--jdk-home"}, description = "JDK home with include/jni.h (defaults to JAVA_HOME)")
        private File jdkHome;

        @CommandLine.Option(names = {"--zig-install-dir"},
                description = "Directory where Zig was installed (defaults to ~/.native-obfuscator/zig)")
        private File zigInstallDir;

        @Override
        public Integer call() throws Exception {
            if (jarFile == null || outputDirectory == null) {
                System.err.println("Missing required arguments: <jarFile> <outputDirectory>");
                System.err.println("Run 'native-obfuscator help' for usage.");
                return 2;
            }

            List<Path> libs = new ArrayList<>();
            if (librariesDirectory != null) {
                Files.walk(librariesDirectory.toPath(), FileVisitOption.FOLLOW_LINKS)
                        .filter(f -> f.toString().endsWith(".jar") || f.toString().endsWith(".zip"))
                        .forEach(libs::add);
            }

            List<String> blackList = new ArrayList<>();
            if (blackListFile != null) {
                blackList = Files.readAllLines(blackListFile.toPath(), StandardCharsets.UTF_8);
            }

            List<String> whiteList = null;
            if (whiteListFile != null) {
                whiteList = Files.readAllLines(whiteListFile.toPath(), StandardCharsets.UTF_8);
            }

            Path outputDir = Paths.get(outputDirectory);
            String nativeDir = new NativeObfuscator().process(jarFile.toPath(), outputDir,
                    libs, blackList, whiteList, libraryName, customLibraryDirectory,
                    platform, useAnnotations, generateDebugJar, codegenMode, backend,
                    irLoweringMode, nativeIntrinsicsMode, cfObfuscation);

            if (useZig) {
                runZigBuild(outputDir, nativeDir);
            }

            return 0;
        }

        private void runZigBuild(Path outputDir, String nativeDir) throws Exception {
            Path zigExe = ZigBuilder.locateZig(
                    zigPath != null ? zigPath.toPath() : null,
                    zigInstallDir != null ? zigInstallDir.toPath() : null);
            if (zigExe == null) {
                throw new IOException("Zig executable not found. Run 'native-obfuscator install-zig' first " +
                        "or pass --zig-path.");
            }

            Path jdkInclude = ZigBuilder.resolveJdkInclude(jdkHome != null ? jdkHome.toPath() : null);
            if (jdkInclude == null) {
                throw new IOException("Could not locate JDK include/ directory. " +
                        "Pass --jdk-home or set JAVA_HOME.");
            }

            List<ZigTarget> targets;
            if (zigTargets == null || zigTargets.isEmpty()) {
                targets = new ArrayList<>();
                targets.add(ZigTarget.host());
            } else {
                targets = zigTargets.stream()
                        .map(s -> s.trim())
                        .filter(s -> !s.isEmpty())
                        .map(s -> "host".equalsIgnoreCase(s) ? ZigTarget.host() : ZigTarget.parse(s))
                        .distinct()
                        .collect(Collectors.toList());
            }

            ZigBuilder.BuildRequest req = new ZigBuilder.BuildRequest();
            req.zigExe = zigExe;
            req.cppDir = outputDir.resolve("cpp");
            req.platform = platform;
            req.jdkInclude = jdkInclude;
            req.targets = targets;
            req.nativeDir = nativeDir;
            if (libraryName != null) {
                // LoaderPlain: shared libs live outside the jar
                req.externalLibsDir = outputDir.resolve("native-libs");
            } else {
                // LoaderUnpack: inject into the produced jar
                req.outputJar = outputDir.resolve(jarFile.getName());
            }

            new ZigBuilder().build(req);
        }
    }

    @CommandLine.Command(name = "install-zig",
            mixinStandardHelpOptions = true,
            description = "Download and install the Zig toolchain locally.")
    static class InstallZigCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"--version"},
                description = "Zig version to install (default: latest stable)")
        private String version;

        @CommandLine.Option(names = {"--install-dir"},
                description = "Install location (default: ~/.native-obfuscator/zig)")
        private File installDir;

        @CommandLine.Option(names = {"--force"},
                description = "Reinstall even if a marker for the same version is present")
        private boolean force;

        @CommandLine.Option(names = {"--index-url"},
                description = "Zig release index URL (default: " + ZigInstaller.DEFAULT_INDEX_URL + ")")
        private String indexUrl;

        @Override
        public Integer call() throws Exception {
            Path root = installDir != null ? installDir.toPath() : ZigInstaller.defaultInstallRoot();
            ZigInstaller installer = new ZigInstaller(root, indexUrl);
            ZigInstaller.Installed result = installer.install(version, force);
            System.out.println("Zig " + result.version + " ready at " + result.exePath);
            return 0;
        }
    }

    public static void main(String[] args) throws IOException {
        System.exit(new CommandLine(new NativeObfuscatorRunner())
                .setCaseInsensitiveEnumValuesAllowed(true).execute(args));
    }
}
