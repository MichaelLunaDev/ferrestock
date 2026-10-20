package pe.edu.cibertec.ferrestock.authreportes.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

/** Version fija: sal 16 bytes + derivado 32 bytes, representados en hexadecimal. */
public final class VersionedPasswordEncoder implements PasswordEncoder {
    public static final String PREFIX = "{pbkdf2-ferrestock-v1}";
    private final Pbkdf2PasswordEncoder delegate;

    public VersionedPasswordEncoder() {
        // En Security 7 este constructor fija 256 bits para SHA256. El vector de
        // compatibilidad y el payload exacto verifican ese ancho sin API deprecada.
        delegate = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        delegate.setEncodeHashAsBase64(false);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        validate(rawPassword);
        String payload = delegate.encode(rawPassword);
        if (!payload.matches("[0-9a-f]{96}")) throw new IllegalStateException("Formato KDF no conforme");
        return PREFIX + payload;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.length() < 12 || rawPassword.length() > 128
                || encodedPassword == null || !encodedPassword.startsWith(PREFIX)) {
            return false;
        }
        String payload = encodedPassword.substring(PREFIX.length());
        if (!payload.matches("[0-9a-f]{96}")) {
            return false;
        }
        return delegate.matches(rawPassword, payload);
    }

    private static void validate(CharSequence raw) {
        if (raw == null || raw.length() < 12 || raw.length() > 128) {
            throw new IllegalArgumentException("Longitud de credencial invalida");
        }
    }
}
