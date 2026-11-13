package com.restaurantmanager.gateway.config;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts device passwords for the configuration file (AES-256-GCM). The master key comes from the
 * {@code GATEWAY_MASTER_KEY} environment variable (32 random bytes, base64), so a copy of the config file alone does
 * not reveal the passwords. Values look like {@code enc:<base64 of iv + ciphertext>}; plain values are used as they are.
 */
public final class SecretBox {
    private static final String PREFIX = "enc:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretBox(byte[] masterKey) {
        if (masterKey == null || masterKey.length != 32) {
            throw new IllegalArgumentException("GATEWAY_MASTER_KEY must be 32 bytes, base64-encoded (run: new-master-key)");
        }
        this.key = new SecretKeySpec(masterKey, "AES");
    }

    public static SecretBox fromEnvironment() {
        String value = System.getenv("GATEWAY_MASTER_KEY");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set GATEWAY_MASTER_KEY first (run: new-master-key)");
        }
        return new SecretBox(Base64.getDecoder().decode(value.trim()));
    }

    public static String newMasterKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    public String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            byte[] all = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt a device password: wrong GATEWAY_MASTER_KEY?", e);
        }
    }
}
