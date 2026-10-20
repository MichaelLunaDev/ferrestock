package pe.edu.cibertec.ferrestock.authreportes.security;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LoginNormalizer {
    private static final Pattern LOGIN = Pattern.compile("[a-z0-9._-]{3,64}");
    private LoginNormalizer() { }

    public static String normalize(String input) {
        if (input == null || !input.chars().allMatch(c -> c < 128)) {
            throw new IllegalArgumentException("Login invalido");
        }
        String canonical = input.trim().toLowerCase(Locale.ROOT);
        if (!LOGIN.matcher(canonical).matches()) {
            throw new IllegalArgumentException("Login invalido");
        }
        return canonical;
    }
}
