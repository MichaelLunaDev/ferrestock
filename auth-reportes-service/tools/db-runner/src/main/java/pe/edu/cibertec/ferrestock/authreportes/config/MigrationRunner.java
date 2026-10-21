package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.datasource.AbstractDataSource;
import static pe.edu.cibertec.ferrestock.authreportes.config.MigrationPolicy.*;

public final class MigrationRunner {
    public static void main(String[] args) {
        int exit = execute(args);
        if (exit != 0) System.exit(exit);
    }
    static int execute(String[] args) {
        String phase = "Configuracion";
        try {
            Request request = Request.parse(args, System.getenv());
            Path module = module();
            try (var snapshot = Snapshot.create(module)) {
                // Read/verify TLS and pinned SQL BEFORE requesting any SQL credential.
                var tls = SqlTlsSettings.from(new StandardEnvironment());
                var console = System.console();
                if (console == null) throw blocked("CONSOLA_SEGURA_REQUERIDA");
                if (request.action() == Action.migrate) {
                    console.printf("Requiere aprobacion administrativa especifica vigente; estado %s; destino %s.%n",
                            request.approvedState(), request.target().database);
                    String confirmation = console.readLine("Confirmar MIGRATE:%s: ", request.target().database);
                    if (!("MIGRATE:" + request.target().database).equals(confirmation)) throw blocked("APROBACION");
                }
                char[] secret = console.readPassword("Credencial de ferrestock_auth_migrator: ");
                if (secret == null || secret.length == 0) throw blocked("CREDENCIAL_AUSENTE");
                DataSource dataSource;
                try {
                    dataSource = guarded(new StrictSqlDataSource(request.target().url(), LOGIN, new String(secret), tls), request.target(), request.ownerVisibilityApproved());
                } finally { Arrays.fill(secret, '\0'); }
                phase = "Precondiciones";
                var flyway = configuration(dataSource, snapshot.directory).load();
                phase = "Ejecucion";
                MigrationFlow.run(request.action(), engine(dataSource, request.target(), flyway));
                System.out.println("{\"Result\":\"PASS\",\"Action\":\"" + request.action()
                        + "\",\"Target\":\"" + request.target() + "\",\"StrictTlsValidated\":true}");
                return 0;
            }
        } catch (Exception ex) {
            System.out.println(failure(phase, ex));
            return 1;
        }
    }
    static String failure(String phase, Exception ex) {
        String code = ex instanceof Blocked blocked ? blocked.code : "OPERACION_RECHAZADA";
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof GuardFailure guard) { code = guard.code; break; }
        }
        // Only codes created by this package; never driver messages, causes, SQL or credentials.
        return "{\"Result\":\"BLOCKED\",\"Phase\":\"" + phase + "\",\"Category\":\"" + code + "\"}";
    }
    static FluentConfiguration configuration(DataSource source, Path location) {
        return Flyway.configure().dataSource(source).schemas("auth").defaultSchema("auth")
                .table("flyway_schema_history").createSchemas(false).cleanDisabled(true)
                .baselineOnMigrate(false).outOfOrder(false).validateOnMigrate(true)
                .validateMigrationNaming(true).failOnMissingLocations(true)
                .ignoreMigrationPatterns(new String[0]).locations("filesystem:" + location.toAbsolutePath())
                .callbacks(new org.flywaydb.core.api.callback.Callback[0]).skipDefaultCallbacks(true)
                .javaMigrations(new org.flywaydb.core.api.migration.JavaMigration[0]).loggers("slf4j");
    }
    static DataSource guarded(DataSource source, Target target) {
        return guarded(source, target, false);
    }
    static final class GuardFailure extends SQLException {
        final String code;
        GuardFailure(String code) { super("Precondicion SQL rechazada", "08001"); this.code = code; }
    }
    static DataSource guarded(DataSource source, Target target, boolean ownerVisibilityApproved) {
        return new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = source.getConnection();
                try { MigrationIdentity.verify(connection, target, ownerVisibilityApproved); return connection; }
                catch (SQLException | RuntimeException ex) {
                    try { connection.close(); } catch (SQLException ignored) { }
                    if (ex instanceof Blocked blocked) throw new GuardFailure(blocked.code);
                    throw new SQLException("Precondicion SQL rechazada", "08001");
                }
            }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                throw new java.sql.SQLFeatureNotSupportedException("Identidad alternativa rechazada");
            }
        };
    }
    private static MigrationFlow.Engine engine(DataSource source, Target target, Flyway flyway) {
        return new MigrationFlow.Engine() {
            public Set<String> objects() throws SQLException {
                try (var connection = source.getConnection()) { return MigrationIdentity.objects(connection); }
            }
            public void inspect(boolean complete) {
                var entries = flyway.info().all();
                var history = new java.util.ArrayList<History>();
                for (var entry : entries) {
                    history.add(new History(entry.getVersion() == null ? null : entry.getVersion().toString(),
                            entry.getScript(), entry.getState().name(), entry.getChecksum()));
                }
                if (MigrationPolicy.history(history, complete)) flyway.validate();
            }
            public void validate() { flyway.validate(); }
            public void migrate() {
                var result = flyway.migrate();
                if (!result.success || result.migrationsExecuted != 2 || !"2".equals(result.targetSchemaVersion))
                    throw blocked("MIGRACION_INCOMPLETA");
            }
        };
    }
    static Path module() throws Exception {
        Path start = Path.of(MigrationRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("pom.xml"))
                    && Files.isDirectory(p.resolve("src/main/resources/db/migration"))) return p;
        }
        throw blocked("MODULO_NO_VERIFICADO");
    }
    static final class Snapshot implements AutoCloseable {
        final Path directory;
        private Snapshot(Path directory) { this.directory = directory; }
        static Snapshot create(Path module) throws Exception {
            Path original = module.resolve("src/main/resources/db/migration");
            if (Files.isSymbolicLink(original)) throw blocked("HASH");
            try (var files = Files.list(original)) {
                if (!files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(PINS.keySet()))
                    throw blocked("MIGRACIONES_ADICIONALES");
            }
            // Read and verify once; Flyway consumes these same bytes, not mutable source files.
            var bytes = new java.util.TreeMap<String, byte[]>();
            for (String name : PINS.keySet()) bytes.put(name, pinned(original, name));
            Files.createDirectories(module.resolve("target"));
            var snapshot = new Snapshot(Files.createTempDirectory(module.resolve("target"), "approved-migrations-"));
            try {
                for (var entry : bytes.entrySet()) Files.write(snapshot.directory.resolve(entry.getKey()), entry.getValue());
                return snapshot;
            } catch (Exception ex) { snapshot.close(); throw ex; }
        }
        @Override public void close() throws Exception {
            // Only exact files created by this invocation; no recursive cleanup of other snapshots.
            for (String name : PINS.keySet()) Files.deleteIfExists(directory.resolve(name));
            Files.deleteIfExists(directory);
        }
    }
    private MigrationRunner() { }
}
