package pe.edu.cibertec.ferrestock.authreportes.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class OpaqueTokens {
    private final SecureRandom random;

    public OpaqueTokens() { this(new SecureRandom()); }
    // Inyeccion acotada al paquete para pruebas deterministas; produccion usa SecureRandom.
    OpaqueTokens(SecureRandom random) { this.random = java.util.Objects.requireNonNull(random); }

    public IssuedToken issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        try {
            return new IssuedToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), digest(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public byte[] hash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Token invalido");
        }
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        try {
            if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) {
                throw new IllegalArgumentException("Token invalido");
            }
            return digest(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 no disponible"); }
    }

    /** Solo para entregar una vez al futuro transporte. No es un modelo persistente. */
    public static final class IssuedToken {
        private final String value;
        private final byte[] hash;
        private IssuedToken(String value, byte[] hash) { this.value = value; this.hash = hash.clone(); }
        public String value() { return value; }
        public byte[] hash() { return hash.clone(); }
        @Override public String toString() { return "IssuedToken[REDACTED]"; }
    }
}
