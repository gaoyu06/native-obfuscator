package by.radioegor146.zig;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and installs the Zig toolchain.
 *
 * Install layout:
 * <pre>
 *   {@literal <}root{@literal >}/
 *     installed.json          (version, exePath)
 *     {@literal <}extracted dir{@literal >}/zig[.exe]
 * </pre>
 */
public class ZigInstaller {

    private static final Logger logger = LoggerFactory.getLogger(ZigInstaller.class);

    public static final String DEFAULT_INDEX_URL = "https://ziglang.org/download/index.json";
    private static final Pattern STABLE_VERSION = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private final Path installRoot;
    private final String indexUrl;

    public ZigInstaller(Path installRoot, String indexUrl) {
        this.installRoot = installRoot;
        this.indexUrl = indexUrl != null ? indexUrl : DEFAULT_INDEX_URL;
    }

    public ZigInstaller(Path installRoot) {
        this(installRoot, null);
    }

    public static Path defaultInstallRoot() {
        String home = System.getProperty("user.home");
        return Paths.get(home, ".native-obfuscator", "zig");
    }

    public static class Installed {
        public final String version;
        public final Path exePath;

        public Installed(String version, Path exePath) {
            this.version = version;
            this.exePath = exePath;
        }
    }

    /** Returns the installed Zig if a valid marker exists and the binary is present. */
    public Installed locate() {
        Path marker = installRoot.resolve("installed.json");
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(marker), StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            String version = json.optString("version", null);
            String exe = json.optString("exePath", null);
            if (version == null || exe == null) return null;
            Path exePath = Paths.get(exe);
            if (!Files.isRegularFile(exePath)) return null;
            return new Installed(version, exePath);
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Install Zig. If {@code requestedVersion} is null, the latest stable release is chosen.
     * If a matching version is already installed, this is a no-op.
     */
    public Installed install(String requestedVersion, boolean force) throws IOException {
        Installed existing = locate();
        if (existing != null && !force &&
                (requestedVersion == null || requestedVersion.equals(existing.version))) {
            logger.info("Zig {} already installed at {}", existing.version, existing.exePath);
            return existing;
        }

        logger.info("Fetching Zig release index from {}", indexUrl);
        JSONObject index = new JSONObject(httpGet(indexUrl));

        String version = requestedVersion != null ? requestedVersion : pickLatestStable(index);
        if (!index.has(version)) {
            throw new IOException("Zig version '" + version + "' not found in index");
        }
        JSONObject release = index.getJSONObject(version);

        // master entries store the actual version in a "version" field
        String actualVersion = release.optString("version", version);

        String hostKey = hostIndexKey();
        if (!release.has(hostKey)) {
            throw new IOException("Zig release " + actualVersion + " has no build for host '" + hostKey + "'");
        }
        JSONObject hostEntry = release.getJSONObject(hostKey);
        String tarballUrl = hostEntry.getString("tarball");
        String expectedShasum = hostEntry.getString("shasum");

        Files.createDirectories(installRoot);
        String fileName = tarballUrl.substring(tarballUrl.lastIndexOf('/') + 1);
        Path archive = installRoot.resolve(fileName);

        logger.info("Downloading {} ({} bytes)", tarballUrl, hostEntry.optString("size", "?"));
        downloadWithSha256(tarballUrl, archive, expectedShasum);

        logger.info("Extracting archive…");
        cleanInstalledExtracts();
        Path extractedRoot = extractArchive(archive, installRoot);
        Files.deleteIfExists(archive);

        Path exe = locateZigExe(extractedRoot);
        if (exe == null) {
            throw new IOException("zig executable not found after extraction in " + extractedRoot);
        }
        ensureExecutable(exe);

        Installed result = new Installed(actualVersion, exe);
        writeMarker(result);
        logger.info("Zig {} installed at {}", result.version, result.exePath);
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String pickLatestStable(JSONObject index) {
        List<String> stable = index.keySet().stream()
                .filter(k -> STABLE_VERSION.matcher(k).matches())
                .sorted(versionComparator().reversed())
                .collect(Collectors.toList());
        if (stable.isEmpty()) {
            throw new IllegalStateException("No stable Zig versions in index");
        }
        return stable.get(0);
    }

    private static Comparator<String> versionComparator() {
        return (a, b) -> {
            String[] ap = a.split("\\.");
            String[] bp = b.split("\\.");
            for (int i = 0; i < Math.max(ap.length, bp.length); i++) {
                int ai = i < ap.length ? Integer.parseInt(ap[i]) : 0;
                int bi = i < bp.length ? Integer.parseInt(bp[i]) : 0;
                if (ai != bi) return Integer.compare(ai, bi);
            }
            return 0;
        };
    }

    private static String hostIndexKey() {
        ZigTarget host = ZigTarget.host();
        return host.arch().zigArch() + "-" + host.os().loaderName();
    }

    private static String httpGet(String url) throws IOException {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "native-obfuscator/zig-installer");
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void downloadWithSha256(String url, Path dest, String expectedShasum) throws IOException {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(120000);
        conn.setRequestProperty("User-Agent", "native-obfuscator/zig-installer");
        long total = conn.getContentLengthLong();
        long downloaded = 0;
        long lastLogged = 0;
        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             OutputStream out = Files.newOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                sha.update(buf, 0, n);
                out.write(buf, 0, n);
                downloaded += n;
                if (total > 0 && downloaded - lastLogged >= total / 10) {
                    logger.info("  … {}% ({} / {} bytes)", downloaded * 100 / total, downloaded, total);
                    lastLogged = downloaded;
                }
            }
        }
        String actual = toHex(sha.digest());
        if (!actual.equalsIgnoreCase(expectedShasum)) {
            Files.deleteIfExists(dest);
            throw new IOException("SHA-256 mismatch for " + url +
                    "\n  expected: " + expectedShasum +
                    "\n  actual:   " + actual);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void cleanInstalledExtracts() throws IOException {
        if (!Files.isDirectory(installRoot)) return;
        try (java.util.stream.Stream<Path> entries = Files.list(installRoot)) {
            entries.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("zig-"))
                    .forEach(ZigInstaller::deleteRecursively);
        }
    }

    private static void deleteRecursively(Path root) {
        try {
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    /** Returns the top-level directory created by extraction (e.g. {@code zig-linux-x86_64-0.13.0}). */
    private static Path extractArchive(Path archive, Path destDir) throws IOException {
        String name = archive.getFileName().toString().toLowerCase();
        if (name.endsWith(".zip")) {
            return extractZip(archive, destDir);
        }
        if (name.endsWith(".tar.xz")) {
            return extractTarXz(archive, destDir);
        }
        throw new IOException("Unsupported archive format: " + archive);
    }

    private static Path extractZip(Path archive, Path destDir) throws IOException {
        Path topLevel = null;
        try (InputStream raw = Files.newInputStream(archive);
             BufferedInputStream bis = new BufferedInputStream(raw);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("Zip slip detected: " + entry.getName());
                }
                if (topLevel == null) {
                    topLevel = destDir.resolve(entry.getName().split("[/\\\\]")[0]);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return topLevel;
    }

    private static Path extractTarXz(Path archive, Path destDir) throws IOException {
        Path topLevel = null;
        try (InputStream raw = Files.newInputStream(archive);
             BufferedInputStream bis = new BufferedInputStream(raw);
             XZCompressorInputStream xz = new XZCompressorInputStream(bis);
             TarArchiveInputStream tar = new TarArchiveInputStream(xz)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextTarEntry()) != null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("Tar slip detected: " + entry.getName());
                }
                if (topLevel == null) {
                    topLevel = destDir.resolve(entry.getName().split("/")[0]);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else if (entry.isSymbolicLink()) {
                    Files.createDirectories(out.getParent());
                    Files.deleteIfExists(out);
                    try {
                        Files.createSymbolicLink(out, Paths.get(entry.getLinkName()));
                    } catch (UnsupportedOperationException | IOException ex) {
                        // Fall back: skip symlinks if FS doesn't support them
                    }
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(tar, out, StandardCopyOption.REPLACE_EXISTING);
                    int mode = entry.getMode();
                    if (mode != 0 && (mode & 0111) != 0) {
                        trySetExecutable(out);
                    }
                }
            }
        }
        return topLevel;
    }

    private static Path locateZigExe(Path root) throws IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fn = p.getFileName().toString();
                        return fn.equals("zig") || fn.equals("zig.exe");
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private static void ensureExecutable(Path exe) {
        if (!exe.getFileName().toString().endsWith(".exe")) {
            trySetExecutable(exe);
        }
    }

    private static void trySetExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            file.toFile().setExecutable(true, false);
        }
    }

    private void writeMarker(Installed installed) throws IOException {
        JSONObject json = new JSONObject();
        json.put("version", installed.version);
        json.put("exePath", installed.exePath.toAbsolutePath().toString());
        Files.write(installRoot.resolve("installed.json"),
                json.toString(2).getBytes(StandardCharsets.UTF_8));
    }
}
