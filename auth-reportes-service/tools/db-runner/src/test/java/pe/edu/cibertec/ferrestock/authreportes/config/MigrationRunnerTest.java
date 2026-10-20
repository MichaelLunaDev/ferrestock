package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.edu.cibertec.ferrestock.authreportes.config.MigrationPolicy.*;

class MigrationRunnerTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(strings = {"main", "Ventas", "AuthTest ", "authtest"})
    void rejectsOtherTargets(String target) {
        assertThatThrownBy(() -> Request.parse(new String[]{target, "info"}, Map.of())).isInstanceOf(Blocked.class);
    }
    @ParameterizedTest @ValueSource(strings = {"clean", "repair", "baseline", "drop", "Migrate"})
    void rejectsOtherOperations(String action) {
        assertThatThrownBy(() -> Request.parse(new String[]{"AuthTest", action}, Map.of())).isInstanceOf(Blocked.class);
    }
    @ParameterizedTest @ValueSource(strings = {"auth-unit", "auth-db", "auth-integration,auth-unit", "auth-integration ", ""})
    void rejectsWrongOrMixedProfile(String profile) {
        assertThatThrownBy(() -> Request.parse(new String[]{"AuthTest", "info"}, Map.of("SPRING_PROFILES_ACTIVE", profile)))
                .isInstanceOf(Blocked.class);
    }
    @Test void rejectsRuntimeIdentity() {
        assertThatThrownBy(() -> Request.parse(new String[]{"AuthTest", "info"}, Map.of("AUTH_SQL_USERNAME", "sa")))
                .isInstanceOf(Blocked.class);
    }
    @Test void exactTargetsAndExplicitMigrationState() {
        assertThat(Request.parse(new String[]{"AuthTest", "migrate", "1/0"}, Map.of()).target()).isEqualTo(Target.AuthTest);
        assertThat(Request.parse(new String[]{"AuthDev", "migrate", "3/1"}, Map.of()).target()).isEqualTo(Target.AuthDev);
        assertThatThrownBy(() -> Request.parse(new String[]{"AuthDev", "migrate", "1/0"}, Map.of())).isInstanceOf(Blocked.class);
        assertThatThrownBy(() -> Request.parse(new String[]{"AuthTest", "migrate"}, Map.of())).isInstanceOf(Blocked.class);
        assertThat(Target.AuthTest.url()).contains("127.0.0.1:14333", "databaseName=ferrestock_auth_test", "encrypt=true", "trustServerCertificate=false");
    }
    @Test void failsBeforeConsoleWhenTlsIsMissing() {
        assertThatThrownBy(() -> SqlTlsSettings.from(new org.springframework.mock.env.MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("jdbc:");
    }
    @Test void modifiedAndUnknownMigrationsRejected() throws Exception {
        Files.writeString(temporary.resolve("V1__auth_core.sql"), "SELECT 'private-canary'");
        assertThatThrownBy(() -> pinned(temporary, "V1__auth_core.sql")).isInstanceOf(Blocked.class).hasMessageNotContaining("private-canary");
        assertThatThrownBy(() -> pinned(temporary, "V3__unreviewed.sql")).isInstanceOf(Blocked.class);
    }
    @Test void actualPinnedFilesSnapshotAndExactCleanup() throws Exception {
        Path module = MigrationRunner.module();
        Path snapshotPath;
        try (var snapshot = MigrationRunner.Snapshot.create(module)) {
            snapshotPath = snapshot.directory;
            for (String name : PINS.keySet()) assertThat(pinned(snapshot.directory, name))
                    .isEqualTo(pinned(module.resolve("src/main/resources/db/migration"), name));
        }
        assertThat(snapshotPath).doesNotExist();
    }
    @Test void extraCallbacksRejectedBeforeSnapshot() throws Exception {
        Path source = temporary.resolve("src/main/resources/db/migration");
        Files.createDirectories(source);
        Files.writeString(source.resolve("beforeMigrate.sql"), "secret");
        assertThatThrownBy(() -> MigrationRunner.Snapshot.create(temporary)).isInstanceOf(Blocked.class);
        assertThat(temporary.resolve("target")).doesNotExist();
    }
    @Test void flywayConfigurationIsClosedAndDoesNotOpenConnection() throws Exception {
        var source = mock(DataSource.class);
        var config = MigrationRunner.configuration(source, temporary);
        assertThat(config.isCleanDisabled()).isTrue();
        assertThat(config.isCreateSchemas()).isFalse();
        assertThat(config.isBaselineOnMigrate()).isFalse();
        assertThat(config.isOutOfOrder()).isFalse();
        assertThat(config.isValidateOnMigrate()).isTrue();
        assertThat(config.isSkipDefaultCallbacks()).isTrue();
        assertThat(config.getCallbacks()).isEmpty();
        assertThat(config.getJavaMigrations()).isEmpty();
        assertThat(config.getSchemas()).containsExactly("auth");
        assertThat(config.getTable()).isEqualTo("flyway_schema_history");
        assertThat(config.getIgnoreMigrationPatterns()).isEmpty();
        assertThat(config.load().getConfiguration().getDataSource()).isSameAs(source);
        verifyNoInteractions(source);
    }
    @Test void sanitizesUnknownExceptionsAndNestedSecrets() {
        var result = MigrationRunner.failure("Ejecucion", new IllegalStateException("password=private-canary; CREATE LOGIN x", new Exception("jdbc:secret")));
        assertThat(result).isEqualTo("{\"Result\":\"BLOCKED\",\"Phase\":\"Ejecucion\",\"Category\":\"OPERACION_RECHAZADA\"}")
                .doesNotContain("password", "private-canary", "CREATE", "jdbc:");
    }
    @Test void failedMigrationBetweenV1AndV2IsNotRetriedOrReportedPass() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of());
        doThrow(new IllegalStateException("V1 committed, V2 failed")).when(engine).migrate();
        assertThatThrownBy(() -> MigrationFlow.run(Action.migrate, engine)).isInstanceOf(IllegalStateException.class);
        verify(engine, times(1)).migrate();
        verify(engine, never()).validate();
        verify(engine, never()).inspect(true);
    }
    @Test void partialStateBlocksAnotherMigration() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of("rol", "usuario", "sesion"));
        assertThatThrownBy(() -> MigrationFlow.run(Action.migrate, engine)).isInstanceOf(Blocked.class);
        verify(engine, never()).migrate();
    }
    @Test void historyFailureBlocksBeforeMutation() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of());
        doThrow(blocked("HISTORIAL")).when(engine).inspect(false);
        assertThatThrownBy(() -> MigrationFlow.run(Action.migrate, engine)).isInstanceOf(Blocked.class);
        verify(engine, never()).migrate();
    }
    @Test void futureFailedPartialAndMismatchedHistoryRejected() {
        var pending1 = new History("1", "V1__auth_core.sql", "PENDING", 1);
        var pending2 = new History("2", "V2__roles.sql", "PENDING", 2);
        var success1 = new History("1", "V1__auth_core.sql", "SUCCESS", 1);
        var success2 = new History("2", "V2__roles.sql", "SUCCESS", 2);
        assertThat(history(List.of(pending1, pending2), false)).isFalse();
        assertThat(history(List.of(success1, success2), true)).isTrue();
        for (var invalid : List.of(List.of(success1, pending2), List.of(success1),
                List.of(pending1, new History("2", "V2__roles.sql", "FAILED", 2)),
                List.of(pending1, new History("2", "V2__roles.sql", "PENDING", null)),
                List.of(pending1, new History("3", "V3__extra.sql", "SUCCESS", 3)),
                List.of(pending1, new History("2", "V1__auth_core.sql", "SUCCESS", 2)))) {
            assertThatThrownBy(() -> history(invalid, false)).isInstanceOf(Blocked.class);
        }
        assertThatThrownBy(() -> history(List.of(pending1, pending2), true)).isInstanceOf(Blocked.class);
    }
    @Test void publicCertificateObserverAlwaysRejectsTrust() {
        var manager = new MigrationCertificateProbe.RejectingTrustManager();
        assertThat(manager.getAcceptedIssuers()).isEmpty();
        assertThatThrownBy(() -> manager.checkServerTrusted(new java.security.cert.X509Certificate[0], "RSA"))
                .isInstanceOf(java.security.cert.CertificateException.class);
        assertThatThrownBy(() -> manager.checkClientTrusted(null, "RSA"))
                .isInstanceOf(java.security.cert.CertificateException.class);
    }
    @Test void postCommitIncompleteObjectsBlock() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of(), Set.of("rol"));
        assertThatThrownBy(() -> MigrationFlow.run(Action.migrate, engine)).isInstanceOf(Blocked.class);
        verify(engine, times(1)).migrate();
        verify(engine, never()).validate();
    }
    @Test void successfulFlowRequiresPostCommitHistoryAndValidate() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of(), OBJECTS);
        MigrationFlow.run(Action.migrate, engine);
        var order = inOrder(engine);
        order.verify(engine).objects(); order.verify(engine).inspect(false); order.verify(engine).migrate();
        order.verify(engine).objects(); order.verify(engine).inspect(true); order.verify(engine).validate();
    }
    @Test void readOnlyInfoNeverMigrates() throws Exception {
        var engine = mock(MigrationFlow.Engine.class);
        when(engine.objects()).thenReturn(Set.of());
        MigrationFlow.run(Action.info, engine);
        verify(engine, never()).migrate(); verify(engine, never()).validate();
    }
    @Test void identitySidOwnerAndExactAclAcceptedWithDoubles() throws Exception {
        MigrationIdentity.verify(connection(Map.of(), false), Target.AuthTest);
    }
    @ParameterizedTest @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21})
    void identitySidOwnerTransactionAndPrivilegeDriftRejected(int column) throws Exception {
        Object value = switch (column) {
            case 1, 2, 3, 4, 5, 7, 9, 10 -> "Unexpected ";
            case 6, 8 -> 0;
            default -> 1;
        };
        assertThatThrownBy(() -> MigrationIdentity.verify(connection(Map.of(column, value), false), Target.AuthTest))
                .isInstanceOf(Blocked.class);
    }
    @Test void unknownMetadataFailsClosed() throws Exception {
        var connection = connection(Map.of(), false, true);
        assertThatThrownBy(() -> MigrationIdentity.verify(connection, Target.AuthTest)).isInstanceOf(Blocked.class)
                .satisfies(ex -> assertThat(((Blocked) ex).code).isEqualTo("METADATO_DESCONOCIDO"));
    }
    @Test void unexpectedAclRejected() throws Exception {
        assertThatThrownBy(() -> MigrationIdentity.verify(connection(Map.of(), true), Target.AuthTest)).isInstanceOf(Blocked.class);
    }
    @Test void autoCommitFalseRejectedBeforeSql() throws Exception {
        var connection = mock(Connection.class);
        assertThatThrownBy(() -> MigrationIdentity.verify(connection, Target.AuthTest)).isInstanceOf(Blocked.class);
        verify(connection, never()).createStatement();
    }
    @Test void guardedConnectionClosedAndDriverMessageHiddenOnFailure() throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenThrow(new java.sql.SQLException("password=private-canary"));
        var guarded = MigrationRunner.guarded(source, Target.AuthTest);
        assertThatThrownBy(guarded::getConnection).isInstanceOf(java.sql.SQLException.class)
                .hasMessage("Precondicion SQL rechazada").hasNoCause();
        verify(connection).close();
        assertThatThrownBy(() -> guarded.getConnection("sa", "private-canary"))
                .isInstanceOf(java.sql.SQLFeatureNotSupportedException.class);
        verify(source, times(1)).getConnection();
    }
    private Connection connection(Map<Integer, Object> changes, boolean extraAcl) throws Exception {
        return connection(changes, extraAcl, false);
    }
    private Connection connection(Map<Integer, Object> changes, boolean extraAcl, boolean unknown) throws Exception {
        var identity = mock(ResultSet.class);
        when(identity.wasNull()).thenReturn(unknown);
        Object[] values = {null, "ferrestock_auth_test", LOGIN, LOGIN, LOGIN, "S", 1, "auth", 1,
                "auth_owner", "S", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        changes.forEach((column, value) -> values[column] = value);
        when(identity.next()).thenReturn(true, false);
        when(identity.getString(anyInt())).thenAnswer(call -> (String) values[call.getArgument(0, Integer.class)]);
        when(identity.getInt(anyInt())).thenAnswer(call -> (Integer) values[call.getArgument(0, Integer.class)]);
        String[] grants = {"CONNECT", "CREATE TABLE", "ALTER", "REFERENCES", "SELECT", "INSERT", "UPDATE", "DELETE", "VIEW DEFINITION"};
        var index = new java.util.concurrent.atomic.AtomicInteger(-1);
        var permissions = mock(ResultSet.class);
        when(permissions.next()).thenAnswer(call -> index.incrementAndGet() < grants.length);
        when(permissions.getInt(anyInt())).thenAnswer(call -> switch (call.getArgument(0, Integer.class)) {
            case 1 -> index.get() < 2 ? 0 : 3;
            case 2 -> index.get() < 2 ? 0 : 5;
            default -> 0;
        });
        when(permissions.getString(anyInt())).thenAnswer(call -> switch (call.getArgument(0, Integer.class)) {
            case 4 -> extraAcl && index.get() == 8 ? "CONTROL" : grants[index.get()];
            case 5 -> "G";
            default -> index.get() < 2 ? "" : "auth";
        });
        var statement = mock(Statement.class);
        when(statement.executeQuery(anyString())).thenReturn(identity, permissions);
        var connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        return connection;
    }
}
