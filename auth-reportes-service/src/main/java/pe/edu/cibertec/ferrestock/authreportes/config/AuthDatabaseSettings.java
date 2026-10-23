package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.core.env.Environment;

/** No record: toString nunca debe mostrar URL ni password. Solo entorno local aprobado. */
public final class AuthDatabaseSettings {
    private final String url;
    private final String user;
    private final String password;
    private final String database;

    private AuthDatabaseSettings(String url, String user, String password, String database) {
        this.url = url; this.user = user; this.password = password; this.database = database;
    }

    public static AuthDatabaseSettings from(Environment env) {
        boolean dev = env.matchesProfiles("auth-db");
        boolean test = env.matchesProfiles("auth-integration");
        if (dev == test || env.matchesProfiles("auth-unit")) throw new IllegalStateException("Seleccionar un unico perfil SQL Auth");
        String prefix = test ? "AUTH_TEST_DB_" : "AUTH_DB_";
        String database = test ? "ferrestock_auth_test" : "ferrestock_auth_dev";
        String expectedUser = test ? "ferrestock_auth_test_app" : "ferrestock_auth_app";
        String url = required(env, prefix + "URL");
        String user = required(env, prefix + "USER");
        String password = required(env, prefix + "PASSWORD");
        // Allowlist cerrada: no propiedades duplicadas, credenciales URL, failover.
        // DEV-LOCAL (22/10/2026): se admite trustServerCertificate=true ademas de false,
        // unicamente para desarrollo local sin JKS provisionado (ver SqlTlsSettings).
        boolean urlValida = url.equals("jdbc:sqlserver://127.0.0.1:14333;databaseName=" + database
                + ";encrypt=true;trustServerCertificate=false")
                || url.equals("jdbc:sqlserver://127.0.0.1:14333;databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true");
        if (!urlValida || !user.equals(expectedUser)) {
            throw new IllegalStateException("Destino o identidad SQL Auth no autorizado");
        }
        return new AuthDatabaseSettings(url, user, password, database);
    }

    private static String required(Environment env, String key) {
        String value;
        try { value = env.getProperty(key); }
        catch (RuntimeException ex) { throw new IllegalStateException("Configuracion SQL Auth incompleta"); }
        if (value == null || value.isBlank()) throw new IllegalStateException("Configuracion SQL Auth incompleta");
        return value;
    }
    String url() { return url; }
    String user() { return user; }
    String password() { return password; }
    String database() { return database; }
    @Override public String toString() { return "AuthDatabaseSettings[REDACTED]"; }
}
