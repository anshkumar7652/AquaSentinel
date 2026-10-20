package com.aquasentinel.infrastructure.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class PBKDF2PasswordHasher {
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int SALT_BYTES = 16;
    public static final int KEY_LENGTH = 256;

    private final SecureRandom secureRandom = new SecureRandom();

    public record HashResult(String hash, String salt, String algorithm, int iterations) {}

    public HashResult hashPassword(char[] password) {
        byte[] saltBytes = new byte[SALT_BYTES];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);

        String hash = computeHash(password, saltBytes, DEFAULT_ITERATIONS, KEY_LENGTH);
        return new HashResult(hash, salt, ALGORITHM, DEFAULT_ITERATIONS);
    }

    public boolean verifyPassword(char[] password, String storedHash, String storedSalt,
                                  String algorithm, int iterations) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(storedSalt);
            String computedHash = computeHash(password, saltBytes, iterations, KEY_LENGTH);
            return MessageDigest.isEqual(
                    computedHash.getBytes(),
                    storedHash.getBytes()
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String computeHash(char[] password, byte[] salt, int iterations, int keyLength) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not generate PBKDF2 password hash", e);
        }
    }
}
