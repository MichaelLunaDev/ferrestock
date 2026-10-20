package pe.edu.cibertec.ferrestock.authreportes.config;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import org.springframework.core.env.Environment;

/** Confianza local explícita. No configura JSSE global ni abre conexiones JDBC. */
final class SqlTlsSettings {
    private final Path path;
    private final Path repository;
    private final String password;

    private SqlTlsSettings(Path path, Path repository, String password) {
        this.path = path;
        this.repository = repository;
        this.password = password;
    }

    static SqlTlsSettings from(Environment environment) {
        try {
            String value = environment.getProperty("SQL_TLS_TRUSTSTORE_PATH");
            String password = environment.getProperty("SQL_TLS_TRUSTSTORE_PASSWORD");
            if (value == null || value.isBlank() || password == null || password.isBlank()) throw invalid();
            Path input = Path.of(value);
            Path repository = repositoryRoot();
            if (!input.isAbsolute() || input.normalize().startsWith(repository)) throw invalid();
            Path real = input.toRealPath();
            var settings = new SqlTlsSettings(real, repository, password);
            settings.verifyFile();
            return settings;
        } catch (Exception ex) {
            throw invalid(); // Sin ruta, password, mensaje del proveedor ni causa.
        }
    }

    void verifyFile() {
        char[] secret = password.toCharArray();
        try {
            Path real = path.toRealPath();
            if (!real.equals(path) || real.startsWith(repository)
                    || !Files.isRegularFile(real) || !Files.isReadable(real)) throw invalid();
            try (var input = new DataInputStream(Files.newInputStream(real))) {
                // JKS de Java puede aceptar PKCS12 en modo compatible: exigir su cabecera real.
                if (input.readInt() != 0xFEEDFEED) throw invalid();
                int version = input.readInt();
                if (version != 1 && version != 2) throw invalid();
            }
            KeyStore store = KeyStore.getInstance("JKS");
            try (var input = Files.newInputStream(real)) { store.load(input, secret); }
            var aliases = store.aliases();
            while (aliases.hasMoreElements()) {
                // Solo anclas públicas; nunca solicitar ni extraer claves de un almacén.
                if (!store.isCertificateEntry(aliases.nextElement())) throw invalid();
            }
        } catch (Exception ex) {
            throw invalid();
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private static Path repositoryRoot() throws Exception {
        Path location = Path.of(SqlTlsSettings.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (Path start : new Path[]{location.toRealPath(), Path.of("").toRealPath()}) {
            for (Path current = start; current != null; current = current.getParent()) {
                if (Files.exists(current.resolve(".git"))) return current.toRealPath();
            }
        }
        // Sin frontera verificable no se puede demostrar que el JKS es externo.
        throw invalid();
    }

    String pathForDriver() { return path.toString(); }
    String passwordForDriver() { return password; }
    private static IllegalStateException invalid() {
        return new IllegalStateException("Configuracion TLS SQL no autorizada o incompleta");
    }
    @Override public String toString() { return "SqlTlsSettings[REDACTED]"; }
}
