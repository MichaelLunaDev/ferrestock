package pe.edu.cibertec.ferrestock.authreportes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import javax.sql.DataSource;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DatabaseConfigTest {
    private static final String SECRET="CONFIG_SECRET_CANARY";
    private static MockEnvironment environment(boolean test) {
        var env = new MockEnvironment(); env.setActiveProfiles(test ? "auth-integration" : "auth-db");
        String prefix=test ? "AUTH_TEST_DB_" : "AUTH_DB_";
        String db=test ? "ferrestock_auth_test" : "ferrestock_auth_dev";
        return env.withProperty(prefix+"URL","jdbc:sqlserver://127.0.0.1:14333;databaseName="+db
                +";encrypt=true;trustServerCertificate=false")
                .withProperty(prefix+"USER",test ? "ferrestock_auth_test_app" : "ferrestock_auth_app")
                .withProperty(prefix+"PASSWORD",SECRET);
    }

    @Test void configInactiveWithoutSqlProfile() {
        new ApplicationContextRunner().withUserConfiguration(AuthDataSourceConfig.class,AuthRepositoryConfig.class)
                .withInitializer(context -> context.setEnvironment(new MockEnvironment()))
                .run(context -> assertThat(context).doesNotHaveBean(DataSource.class));
    }

    @Test void settingsUseExactIdentityPerEnvironmentWithoutPrintingSecrets() {
        var dev=AuthDatabaseSettings.from(environment(false));
        var test=AuthDatabaseSettings.from(environment(true));
        assertThat(dev.database()).isEqualTo("ferrestock_auth_dev");
        assertThat(test.database()).isEqualTo("ferrestock_auth_test");
        assertThat(test.user()).isEqualTo("ferrestock_auth_test_app");
        assertThat(dev.toString()+test.toString()).doesNotContain(SECRET,"jdbc:");
    }

    @Test void conflictingProfilesAreRejectedBeforeConnecting() {
        var env=environment(false); env.setActiveProfiles("auth-db","auth-integration");
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env));
    }

    @Test void configurationResolutionFailureDoesNotExposeUnknownMessages() {
        var env=environment(true).withProperty("AUTH_TEST_DB_PASSWORD","${UNRESOLVED_SECRET_CANARY}");
        var failure=catchThrowable(() -> AuthDatabaseSettings.from(env));
        assertThat(failure).hasMessage("Configuracion SQL Auth incompleta").hasNoCause();
    }

    @Test void testProfileCannotFallBackToDev() {
        var env=environment(false); env.setActiveProfiles("auth-integration");
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env))
                .withMessage("Configuracion SQL Auth incompleta");
    }

    @ParameterizedTest @ValueSource(strings={"auth-db","auth-integration"})
    void missingConfigurationFailsBeforeCreatingDataSource(String profile,CapturedOutput output) {
        new ApplicationContextRunner().withUserConfiguration(AuthDataSourceConfig.class)
                .withInitializer(context -> {
                    var env=new MockEnvironment(); env.setActiveProfiles(profile); context.setEnvironment(env);
                })
                .run(context -> assertThat(context).hasFailed());
        assertThat(output).doesNotContain(SECRET);
    }

    @ParameterizedTest @ValueSource(strings={"sa","ferrestock_auth_migrator","ferrestock_auth_app"})
    void testCannotUseOtherSqlUsers(String user) {
        var env=environment(true).withProperty("AUTH_TEST_DB_USER",user);
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env));
    }

    @ParameterizedTest @ValueSource(strings={";password=URL_SECRET", ";databaseName=ferrestock_auth_dev",
            ";trustServerCertificate=true", ";user=sa"})
    void rejectsInjectedOrDuplicateUrlProperties(String suffix) {
        var env=environment(true);
        env.withProperty("AUTH_TEST_DB_URL",env.getProperty("AUTH_TEST_DB_URL")+suffix);
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env))
                .withMessage("Destino o identidad SQL Auth no autorizado");
    }

    @Test void identityPassesWithReadOnlyQueryAndClosesResources() throws Exception {
        var ds=mock(DataSource.class); var connection=mock(Connection.class);
        var statement=mock(Statement.class); var row=mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(row); when(row.next()).thenReturn(true);
        when(row.getString(1)).thenReturn("ferrestock_auth_test");
        when(row.getString(2)).thenReturn("ferrestock_auth_test_app");
        when(row.getString(3)).thenReturn("ferrestock_auth_test_app");
        DatabaseIdentity.verify(ds,AuthDatabaseSettings.from(environment(true)));
        verify(statement).executeQuery("SELECT DB_NAME(), ORIGINAL_LOGIN(), USER_NAME()");
        verify(row).close(); verify(statement).close(); verify(connection).close();
    }

    @Test void identityMismatchBlocksEvenIfUrlLooksCorrect() throws Exception {
        var ds=mock(DataSource.class); var connection=mock(Connection.class);
        var statement=mock(Statement.class); var row=mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(row); when(row.next()).thenReturn(true);
        when(row.getString(1)).thenReturn("ferrestock_auth_dev");
        assertThatIllegalStateException().isThrownBy(() -> DatabaseIdentity.verify(ds,AuthDatabaseSettings.from(environment(true))))
                .withMessage("Identidad SQL Auth no conforme");
    }

    @Test void driverExceptionIsSanitizedWithoutCauseOrLogs(CapturedOutput output) throws Exception {
        var ds=mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException(SECRET+" jdbc:secret;password=SECRET"));
        var failure=catchThrowable(() -> DatabaseIdentity.verify(ds,AuthDatabaseSettings.from(environment(true))));
        assertThat(failure).hasMessage("No se pudo verificar identidad SQL Auth").hasNoCause();
        assertThat(output).doesNotContain(SECRET,"jdbc:secret");
    }
}
