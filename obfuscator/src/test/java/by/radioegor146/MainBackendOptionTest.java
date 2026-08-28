package by.radioegor146;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MainBackendOptionTest {

    @Test
    public void backendDefaultsToCpp() throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("input.jar", "output");

        assertEquals(CompilerBackend.CPP, backendOf(runner));
    }

    @Test
    public void backendAcceptsInterpreterCaseInsensitively() throws Exception {
        Main.NativeObfuscatorRunner runner = new Main.NativeObfuscatorRunner();
        new CommandLine(runner).setCaseInsensitiveEnumValuesAllowed(true)
                .parseArgs("--backend=InTeRpReTeR", "input.jar", "output");

        assertEquals(CompilerBackend.INTERPRETER, backendOf(runner));
    }

    private static CompilerBackend backendOf(Main.NativeObfuscatorRunner runner) throws Exception {
        Field field = Main.NativeObfuscatorRunner.class.getDeclaredField("backend");
        field.setAccessible(true);
        return (CompilerBackend) field.get(runner);
    }
}
