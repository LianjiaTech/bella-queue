package com.ke.bella.batch.utils;

import com.ke.bella.batch.service.Configs;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class EncryptUtilsTest {

    private static final String TEST_SECRET_KEY = "1234567890123456"; // 16 bytes for AES

    @Before
    public void setUp() {
        try {
            // Set the Configs.SECRET_KEY first
            Configs.SECRET_KEY = TEST_SECRET_KEY;

            // Use reflection to set the final SECRET_KEY field in EncryptUtils
            Field secretKeyField = EncryptUtils.class.getDeclaredField("SECRET_KEY");
            secretKeyField.setAccessible(true);

            // Remove final modifier
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(secretKeyField, secretKeyField.getModifiers() & ~Modifier.FINAL);

            // Set the value
            secretKeyField.set(null, TEST_SECRET_KEY);
        } catch (Exception e) {
            throw new RuntimeException("Failed to setup test environment", e);
        }
    }

    @Test
    public void testEncryptDecrypt_BasicString() {
        String originalText = "Hello World";

        String encrypted = EncryptUtils.encrypt(originalText);
        String decrypted = EncryptUtils.decrypt(encrypted);

        assertNotNull(encrypted);
        assertNotEquals(originalText, encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testEncryptDecrypt_EmptyString() {
        String originalText = "";

        String encrypted = EncryptUtils.encrypt(originalText);
        String decrypted = EncryptUtils.decrypt(encrypted);

        assertNotNull(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testEncryptDecrypt_SpecialCharacters() {
        String originalText = "Special!@#$%^&*()_+={}[]|\\:;\"'<>,.?/~`";

        String encrypted = EncryptUtils.encrypt(originalText);
        String decrypted = EncryptUtils.decrypt(encrypted);

        assertNotNull(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testEncryptDecrypt_UnicodeCharacters() {
        String originalText = "测试中文字符 🚀 emoji";

        String encrypted = EncryptUtils.encrypt(originalText);
        String decrypted = EncryptUtils.decrypt(encrypted);

        assertNotNull(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testEncryptDecrypt_LongString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("A");
        }
        String originalText = sb.toString();

        String encrypted = EncryptUtils.encrypt(originalText);
        String decrypted = EncryptUtils.decrypt(encrypted);

        assertNotNull(encrypted);
        assertEquals(originalText, decrypted);
    }

    @Test
    public void testEncrypt_SameInputProducesSameOutput() {
        String originalText = "consistent test";

        String encrypted1 = EncryptUtils.encrypt(originalText);
        String encrypted2 = EncryptUtils.encrypt(originalText);

        assertEquals(encrypted1, encrypted2);
    }

    @Test(expected = RuntimeException.class)
    public void testDecrypt_InvalidBase64() {
        EncryptUtils.decrypt("invalid-base64-string!");
    }

    @Test(expected = RuntimeException.class)
    public void testDecrypt_ValidBase64ButInvalidEncryption() {
        EncryptUtils.decrypt("dGhpc2lzYXZhbGlkYmFzZTY0c3RyaW5n"); // "thisisavalidbase64string"
    }

    @Test
    public void testEncrypt_ReturnsBase64String() {
        String originalText = "test encryption";

        String encrypted = EncryptUtils.encrypt(originalText);

        // Verify it's a valid base64 string
        assertNotNull(encrypted);
        assertTrue(encrypted.matches("^[A-Za-z0-9+/]*={0,2}$"));
    }
}
