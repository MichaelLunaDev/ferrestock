package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.List;

final class MigrationPolicy {
    static final String LOGIN = "ferrestock_auth_migrator";
    static final Map<String, String> PINS = Map.of(
        "V1__auth_core.sql", "a3c4be473c176533efa484927153c5c09ce2b4d40f2ae1c3e236aef45ded4a07",
        "V2__roles.sql", "c894a3e4798b8a8a6cd90ea693af1d0b52606a30623712bb8eaeb37a16acf624");
    static final Set<String> OBJECTS = Set.of("rol", "usuario", "sesion", "flyway_schema_history");
    enum Action { info, validate, migrate }
    enum Target {
        AuthTest("ferrestock_auth_test", "auth-integration", "1/0"),
        AuthDev("ferrestock_auth_dev", "auth-db", "3/1");
        final String database, profile, approvedState;
        Target(String database, String profile, String approvedState) {
            this.database = database; this.profile = profile; this.approvedState = approvedState;
        }
        String url() { return "jdbc:sqlserver://127.0.0.1:14333;databaseName=" + database
                + ";encrypt=true;trustServerCertificate=false;applicationName=FerreStockMigration;enlist=false"; }
    }
    record Request(Target target, Action action, String approvedState) {
        static Request parse(String[] args, Map<String, String> env) {
            try {
                if (args.length < 2 || args.length > 3) throw blocked("ARGUMENTOS");
                var target = Target.valueOf(args[0]);
                var action = Action.valueOf(args[1]);
                String profile = env.get("SPRING_PROFILES_ACTIVE");
                if (profile != null && !profile.equals(target.profile)) throw blocked("PERFIL");
                String login = env.get("AUTH_SQL_USERNAME");
                if (login != null && !login.equals(LOGIN)) throw blocked("IDENTIDAD_CONFIGURADA");
                String state = args.length == 3 ? args[2] : "";
                if (action == Action.migrate && !state.equals(target.approvedState)) throw blocked("APROBACION_ESTADO");
                if (action != Action.migrate && args.length != 2) throw blocked("ARGUMENTOS");
                return new Request(target, action, state);
            } catch (IllegalArgumentException ex) { throw blocked("ARGUMENTOS"); }
        }
    }
    static byte[] pinned(Path directory, String name) throws Exception {
        if (!PINS.containsKey(name)) throw blocked("MIGRACION_DESCONOCIDA");
        Path file = directory.resolve(name);
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) throw blocked("HASH");
        byte[] bytes = Files.readAllBytes(file);
        if (!PINS.get(name).equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))))
            throw blocked("HASH");
        return bytes;
    }
    static void objects(Set<String> names, boolean firstMigration) {
        if (firstMigration ? !names.isEmpty() : !(names.isEmpty() || names.equals(OBJECTS))) throw blocked("OBJETOS");
    }
    record History(String version, String script, String state, Integer checksum) { }
    static boolean history(List<History> entries, boolean complete) {
        Set<String> versions = new java.util.HashSet<>();
        int applied = 0;
        for (var entry : entries) {
            String expected = "1".equals(entry.version()) ? "V1__auth_core.sql"
                    : "2".equals(entry.version()) ? "V2__roles.sql" : "";
            if (expected.isEmpty() || !expected.equals(entry.script()) || !versions.add(entry.version())
                    || entry.checksum() == null || !("PENDING".equals(entry.state()) || "SUCCESS".equals(entry.state())))
                throw blocked("HISTORIAL");
            if ("SUCCESS".equals(entry.state())) applied++;
        }
        if (entries.size() != 2 || !versions.equals(Set.of("1", "2")) || (applied != 0 && applied != 2)
                || (complete && applied != 2)) throw blocked("HISTORIAL");
        return applied == 2;
    }
    static Blocked blocked(String code) { return new Blocked(code); }
    static final class Blocked extends RuntimeException {
        final String code;
        Blocked(String code) { super("Operacion bloqueada"); this.code = code; }
    }
    private MigrationPolicy() { }
}
