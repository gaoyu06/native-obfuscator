package by.radioegor146;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainBackendOptionTest {

    @Test
    public void backendDefaultsToCpp() throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("input.jar", "output");

        assertEquals(CompilerBackend.CPP, backendOf(runner));
        assertFalse(booleanField(runner, "publishNativeLib"));
    }

    @Test
    public void backendAcceptsInterpreterCaseInsensitively() throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("--backend=InTeRpReTeR", "input.jar", "output");

        assertEquals(CompilerBackend.INTERPRETER, backendOf(runner));
    }

    @Test
    public void acceptsLinkOnlyPublicationAndOpcodeSeed() throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("--backend=interpreter", "--publish-native-lib",
                        "--opcode-seed=42", "input.jar", "output");

        assertEquals(CompilerBackend.INTERPRETER, backendOf(runner));
        assertTrue(booleanField(runner, "publishNativeLib"));
        assertEquals(42L, field(runner, "opcodeSeed"));
    }

    private static CompilerBackend backendOf(Main.NativeObfuscatorRunner runner) throws Exception {
        return (CompilerBackend) field(runner, "backend");
    }

    private static boolean booleanField(Main.NativeObfuscatorRunner runner, String name)
            throws Exception {
        return (Boolean) field(runner, name);
    }

    private static Object field(Main.NativeObfuscatorRunner runner, String name) throws Exception {
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(runner);
    }
}
