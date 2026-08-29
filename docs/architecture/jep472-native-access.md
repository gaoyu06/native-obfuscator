# JEP 472 native-access packaging

## Context

Generated JARs load their native payload through `native0.Loader`,
`native0.LoaderUnpack`, and `native0.LoaderPlain`, which call
`System.load` / `System.loadLibrary`. [JEP 472](https://openjdk.org/jeps/472)
classifies those as *restricted* methods. On JDK 24+ an unenabled call prints a
warning by default; a future release may make denial the default.

The landed JDK 25 IR end-to-end run
(`docs/benchmarks/ir-jdk25-e2e-corpus.md`, commit `5ac6ec5`) recorded the
four-line JEP 472 warning on every transformed run and noted the future-error
risk. This increment is a packaging + documentation response to that finding. It
is **not** a JDK 25 support badge and does not change how the loader calls
`System.load`.

## What the tool does

When writing the output JAR, `NativeObfuscator` now always emits a manifest
(`META-INF/MANIFEST.MF`) whose main attributes include:

```text
Enable-Native-Access: ALL-UNNAMED
```

Rules, implemented in `NativeObfuscator.buildOutputManifest`:

1. If the input JAR already declares a more specific `Enable-Native-Access`
   value, that value is preserved rather than overwritten.
2. All other input manifest attributes (for example `Main-Class`) are carried
   through unchanged.
3. If the input JAR has no manifest, a minimal one is created with
   `Manifest-Version: 1.0` plus the attribute.

## What it does and does not cover

- `java -jar <output.jar>` (the application JAR) honors the
  `Enable-Native-Access` manifest attribute, so the unnamed module is granted
  native access without a command-line flag.
- A classpath launch (`java -cp <output.jar> <main-class>`) does **not** honor
  the manifest attribute and still requires
  `--enable-native-access=ALL-UNNAMED`.
- The default policy on current JDKs remains warning/allow. Future default
  denial remains a risk this attribute mitigates for `java -jar` launches only.

## What did not change

- No new CLI flag was added.
- `--codegen`, `--ir-lower`, and `--backend` defaults are unchanged.
- The loader load calls are unchanged.
- The JDK 25 E2E warning result in
  `docs/benchmarks/ir-jdk25-e2e-corpus.md` was not re-run and is not restated as
  resolved.
