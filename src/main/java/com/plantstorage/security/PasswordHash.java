package com.plantstorage.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;

public final class PasswordHash {
    public static final int ITERATIONS = 600_000;
    private PasswordHash() {}
    public static String create(char[] password) {
        if (password.length < 12 || password.length > 256)
            throw new IllegalArgumentException("Use a password/passphrase between 12 and 256 characters.");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2-sha256:" + ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
                + ":" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static void validate(String encoded) { parts(encoded); }
    private static String[] parts(String encoded) {
        try {
            String[] parts = encoded.split(":", -1);
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256") ||
                Integer.parseInt(parts[1]) < ITERATIONS || Integer.parseInt(parts[1]) > 2_000_000 ||
                Base64.getDecoder().decode(parts[2]).length != 16 ||
                Base64.getDecoder().decode(parts[3]).length != 32) throw new IllegalArgumentException();
            return parts;
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid APP_PASSWORD_HASH. Run ConfigureLogin to generate it."); }
    }
    public static boolean verify(char[] password, String encoded) {
        String[] parts = parts(encoded);
        if (password.length > 256) return false;
        byte[] actual = derive(password, Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]));
        try { return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[3])); }
        finally { Arrays.fill(actual, (byte)0); }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException("Password hashing unavailable", ex); }
        finally { spec.clearPassword(); }
    }
}
