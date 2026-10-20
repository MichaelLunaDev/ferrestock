package pe.edu.cibertec.ferrestock.authreportes.config;

import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.jdbc.*;
import pe.edu.cibertec.ferrestock.authreportes.security.VersionedPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

/** Explicit Failsafe opt-in only. Real AuthTest SQL; no Spring HTTP context or mocks. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthSqlIT {
    private DataSource source;
    private NamedParameterJdbcTemplate jdbc;
    private JdbcUsuarioRepository users;
    private JdbcSesionRepository sessions;
    private TransactionTemplate transaction;

    @BeforeAll void approvedAuthTestOnly() throws Exception {
        checkApproval(System.getenv());
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("auth-integration");
        var config = new AuthDataSourceConfig();
        source = config.authDataSource(environment); // Fixed URL, runtime identity and strict TLS.
        jdbc = config.authJdbc(source);
        users = new JdbcUsuarioRepository(jdbc);
        sessions = new JdbcSesionRepository(jdbc);
        transaction = new TransactionTemplate(new JdbcTransactionManager(source));
        transaction.setTimeout(15);
        assertThat(jdbc.queryForList("SELECT rol_id FROM auth.rol ORDER BY rol_id", Map.of(), Integer.class))
                .containsExactly(1, 2, 3);
        verifyRuntimePermissions(); // Must succeed before any fixture, independently of test order.
    }
    static void checkApproval(Map<String, String> env) {
        if (!"true".equals(env.get("FERRESTOCK_AUTH_TEST_FIXTURES_APPROVED")))
            throw new IllegalStateException("Integracion SQL requiere aprobacion expresa de fixtures AuthTest");
        String requested = env.get("SPRING_PROFILES_ACTIVE");
        if (requested != null && !requested.equals("auth-integration"))
            throw new IllegalStateException("Perfil de integracion no autorizado");
    }

    @Test void actualIdentityAndRuntimeLeastPrivilege() throws Exception {
        verifyRuntimePermissions();
    }
    private void verifyRuntimePermissions() throws Exception {
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var row = statement.executeQuery("""
                    SELECT DB_NAME(), ORIGINAL_LOGIN(), USER_NAME(),
                      HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CREATE TABLE'),
                      HAS_PERMS_BY_NAME(N'auth','SCHEMA','ALTER'),
                      HAS_PERMS_BY_NAME(N'auth.usuario','OBJECT','DELETE'),
                      HAS_PERMS_BY_NAME(N'auth.sesion','OBJECT','DELETE'),
                      HAS_PERMS_BY_NAME(N'auth.rol','OBJECT','INSERT'),
                      HAS_PERMS_BY_NAME(N'auth.usuario','OBJECT','INSERT'),
                      HAS_PERMS_BY_NAME(N'auth.sesion','OBJECT','UPDATE')
                    """)) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("ferrestock_auth_test");
                assertThat(row.getString(2)).isEqualTo("ferrestock_auth_test_app");
                assertThat(row.getString(3)).isEqualTo("ferrestock_auth_test_app");
                for (int column = 4; column <= 8; column++) {
                    assertThat(row.getInt(column)).isZero(); assertThat(row.wasNull()).isFalse();
                }
                for (int column = 9; column <= 10; column++) {
                    assertThat(row.getInt(column)).isOne(); assertThat(row.wasNull()).isFalse();
                }
            }
        }
    }
    @Test void actualTransactionGuardAndRollback() {
        String login = login();
        assertThatThrownBy(() -> users.findByIdForUpdate(Long.MAX_VALUE)).isInstanceOf(IllegalStateException.class);
        transaction.executeWithoutResult(status -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            long id = fixture(login);
            assertThat(users.findByIdForUpdate(id)).isPresent();
            assertThat(users.lockActiveAdministrators()).doesNotContain(id);
            status.setRollbackOnly();
        });
        assertThat(users.findByLogin(login)).isEmpty();
    }
    @Test void actualCompareAndSwapRejectsStaleSnapshot() {
        transaction.executeWithoutResult(status -> {
            long id = fixture(login());
            var initial = users.findByIdForUpdate(id).orElseThrow();
            assertThat(users.updateProfile(id, "Nombre posterior", 1, initial.actualizadoEn(), id)).isTrue();
            assertThat(users.updateProfile(id, "Nombre obsoleto", 1, initial.actualizadoEn(), id)).isFalse();
            assertThat(users.findById(id).orElseThrow().nombre()).isEqualTo("Nombre posterior");
            status.setRollbackOnly();
        });
    }
    @Test void actualSessionLookupAndImmediateRevocation() {
        transaction.executeWithoutResult(status -> {
            long id = fixture(login());
            byte[] hash = randomHash();
            Instant now = users.databaseNow();
            long session = sessions.insert(new Sesion(null, id, hash, 1, now, now.plusSeconds(300), null, null));
            assertThat(sessions.findActiveByHash(hash)).isPresent();
            assertThat(sessions.revoke(session, id, MotivoRevocacion.LOGOUT)).isOne();
            assertThat(sessions.findActiveByHash(hash)).isEmpty();
            assertThat(sessions.revoke(session, id, MotivoRevocacion.LOGOUT)).isZero();
            status.setRollbackOnly();
        });
    }
    @Test void actualDatabaseClockRejectsExpiredSession() {
        transaction.executeWithoutResult(status -> {
            long id = fixture(login());
            byte[] hash = randomHash();
            Instant now = users.databaseNow();
            sessions.insert(new Sesion(null, id, hash, 1, now.minusSeconds(120), now.minusSeconds(60), null, null));
            assertThat(sessions.findActiveByHash(hash)).isEmpty();
            status.setRollbackOnly();
        });
    }
    @Test void actualSecurityVersionInvalidatesSession() {
        transaction.executeWithoutResult(status -> {
            long id = fixture(login());
            byte[] hash = randomHash();
            Instant now = users.databaseNow();
            sessions.insert(new Sesion(null, id, hash, 1, now, now.plusSeconds(300), null, null));
            var user = users.findByIdForUpdate(id).orElseThrow();
            assertThat(sessions.findActiveByHash(hash)).isPresent();
            // Keep ACTIVO: otherwise deactivation could conceal a broken version predicate.
            var updated = new Usuario(id, user.login(), user.nombre(), user.passwordHash(), EstadoUsuario.ACTIVO,
                    user.rol(), 2, user.creadoEn(), user.actualizadoEn(), null, id);
            assertThat(users.updateSecurity(updated, 1)).isTrue();
            assertThat(sessions.findActiveByHash(hash)).isEmpty();
            status.setRollbackOnly();
        });
    }
    private long fixture(String login) {
        String hash = new VersionedPasswordEncoder().encode(UUID.randomUUID().toString());
        return users.insert(new Usuario(null, login, "Fixture integracion AuthTest", hash,
                EstadoUsuario.ACTIVO, Rol.VENDEDOR, 1, null, null, null, null));
    }
    private static String login() { return "it." + UUID.randomUUID().toString().replace("-", ""); }
    private static byte[] randomHash() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes); return bytes;
    }
}
