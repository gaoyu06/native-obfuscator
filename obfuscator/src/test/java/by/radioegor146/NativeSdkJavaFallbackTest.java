package by.radioegor146;

import by.radioegor146.nativeobfuscator.NativePrimitives;
import by.radioegor146.nativeobfuscator.NativeStrings;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeSdkJavaFallbackTest {
    @Test
    public void sha256MatchesKnownVectorWithoutNativeLibrary() {
        byte[] digest = NativePrimitives.sha256(
                "abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                hex(digest));
        assertEquals(1, NativePrimitives.abiVersion());
    }

    @Test
    public void stringOpsMatchJavaWithoutNativeLibrary() {
        assertEquals("hi".length(), NativeStrings.length("hi"));
        assertEquals("hi".hashCode(), NativeStrings.hashCode("hi"));
        assertEquals("hi".concat("!"), NativeStrings.concat("hi", "!"));
    }

    @Test
    public void constantTimeEqualsMatchesContent() {
        assertTrue(NativePrimitives.constantTimeEquals(
                new byte[] {1, 2}, new byte[] {1, 2}));
        assertFalse(NativePrimitives.constantTimeEquals(
                new byte[] {1, 2}, new byte[] {1, 3}));
        assertFalse(NativePrimitives.constantTimeEquals(
                new byte[] {1}, new byte[] {1, 2}));
    }

    @Test
    public void legacySdkDelegatesToAnnotationSdk() {
        byte[] expected = NativePrimitives.sha256(
                "abc".getBytes(StandardCharsets.US_ASCII));
        assertArrayEquals(expected, by.radioegor146.sdk.NativePrimitives.sha256(
                "abc".getBytes(StandardCharsets.US_ASCII)));
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            text.append(String.format("%02x", value & 0xff));
        }
        return text.toString();
    }
}
