package by.radioegor146.zig;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Mapping between user-facing target names (matching LoaderUnpack's expected
 * library file naming, e.g. {@code x64-windows}), Zig target triples and
 * resulting shared-library file names.
 */
public final class ZigTarget {

    public enum Os {
        LINUX("linux", ".so"),
        WINDOWS("windows", ".dll"),
        MACOS("macos", ".dylib");

        private final String loaderName;
        private final String libExt;

        Os(String loaderName, String libExt) {
            this.loaderName = loaderName;
            this.libExt = libExt;
        }

        public String loaderName() {
            return loaderName;
        }

        public String libExt() {
            return libExt;
        }
    }

    public enum Arch {
        X64("x64", "x86_64"),
        X86("x86", "x86"),
        ARM64("arm64", "aarch64"),
        ARM32("arm32", "arm");

        private final String loaderName;
        private final String zigArch;

        Arch(String loaderName, String zigArch) {
            this.loaderName = loaderName;
            this.zigArch = zigArch;
        }

        public String loaderName() {
            return loaderName;
        }

        public String zigArch() {
            return zigArch;
        }
    }

    private final Arch arch;
    private final Os os;

    public ZigTarget(Arch arch, Os os) {
        this.arch = arch;
        this.os = os;
    }

    public Arch arch() {
        return arch;
    }

    public Os os() {
        return os;
    }

    /**
     * Zig {@code -target} triple. ABI choice: {@code gnu} for Linux/Windows
     * (zig ships a complete glibc/mingw toolchain), nothing for macOS.
     */
    public String zigTriple() {
        switch (os) {
            case LINUX:
                if (arch == Arch.ARM32) {
                    return "arm-linux-gnueabihf";
                }
                return arch.zigArch() + "-linux-gnu";
            case WINDOWS:
                return arch.zigArch() + "-windows-gnu";
            case MACOS:
                return arch.zigArch() + "-macos";
            default:
                throw new IllegalStateException(os.name());
        }
    }

    /** File name expected by the bundled LoaderUnpack, e.g. {@code x64-windows.dll}. */
    public String loaderFileName() {
        return arch.loaderName() + "-" + os.loaderName() + os.libExt();
    }

    /** Sub-directory under {@code <jdk>/include} that contains {@code jni_md.h}. */
    public String jniMdSubdir() {
        switch (os) {
            case LINUX:
                return "linux";
            case WINDOWS:
                return "win32";
            case MACOS:
                return "darwin";
            default:
                throw new IllegalStateException(os.name());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZigTarget)) return false;
        ZigTarget that = (ZigTarget) o;
        return arch == that.arch && os == that.os;
    }

    @Override
    public int hashCode() {
        return Objects.hash(arch, os);
    }

    @Override
    public String toString() {
        return arch.loaderName() + "-" + os.loaderName();
    }

    // ------------------------------------------------------------------
    // Friendly-name parsing and host detection
    // ------------------------------------------------------------------

    private static final Map<String, ZigTarget> ALIASES;

    static {
        Map<String, ZigTarget> m = new LinkedHashMap<>();
        for (Arch a : Arch.values()) {
            for (Os o : Os.values()) {
                // skip combos zig doesn't ship by default
                if (a == Arch.ARM32 && o != Os.LINUX) continue;
                if (a == Arch.X86 && o == Os.MACOS) continue;
                if (a == Arch.ARM64 && o == Os.WINDOWS) {
                    // zig supports it; keep it
                }
                m.put(a.loaderName() + "-" + o.loaderName(), new ZigTarget(a, o));
            }
        }
        ALIASES = Collections.unmodifiableMap(m);
    }

    public static ZigTarget parse(String friendlyName) {
        String key = friendlyName.toLowerCase(Locale.ROOT).trim();
        ZigTarget target = ALIASES.get(key);
        if (target == null) {
            throw new IllegalArgumentException("Unknown zig target '" + friendlyName +
                    "'. Known targets: " + ALIASES.keySet());
        }
        return target;
    }

    public static List<String> knownTargets() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(ALIASES.keySet()));
    }

    /** Detect the current host as a ZigTarget. */
    public static ZigTarget host() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        Os os;
        if (osName.contains("win")) os = Os.WINDOWS;
        else if (osName.contains("mac") || osName.contains("darwin")) os = Os.MACOS;
        else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) os = Os.LINUX;
        else throw new IllegalStateException("Unsupported host OS: " + osName);

        Arch arch;
        if (Arrays.asList("amd64", "x86_64", "x64").contains(osArch)) arch = Arch.X64;
        else if (Arrays.asList("aarch64", "arm64").contains(osArch)) arch = Arch.ARM64;
        else if (osArch.startsWith("arm")) arch = Arch.ARM32;
        else if (Arrays.asList("x86", "i386", "i686").contains(osArch)) arch = Arch.X86;
        else throw new IllegalStateException("Unsupported host arch: " + osArch);

        return new ZigTarget(arch, os);
    }
}
