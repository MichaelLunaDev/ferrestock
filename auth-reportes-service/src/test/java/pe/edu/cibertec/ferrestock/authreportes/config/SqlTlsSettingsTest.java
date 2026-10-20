package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import pe.edu.cibertec.ferrestock.authreportes.reportes.CommercialReportReader;
import pe.edu.cibertec.ferrestock.authreportes.reportes.UnconfiguredCommercialReader;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class SqlTlsSettingsTest {
    @TempDir Path temp;

    private void rejects(MockEnvironment environment) {
        assertThat(catchThrowable(() -> SqlTlsSettings.from(environment)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Configuracion TLS SQL no autorizada o incompleta").hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings={"SQL_TLS_TRUSTSTORE_PATH","SQL_TLS_TRUSTSTORE_PASSWORD"})
    void missingConfigurationIsRejected(String missing) throws Exception {
        var env = new MockEnvironment();
        if (!missing.endsWith("PATH")) env.withProperty("SQL_TLS_TRUSTSTORE_PATH",TlsFixtures.store(temp).toString());
        if (!missing.endsWith("PASSWORD")) env.withProperty("SQL_TLS_TRUSTSTORE_PASSWORD",TlsFixtures.STORE_PASSWORD);
        rejects(env);
    }

    @ParameterizedTest @ValueSource(strings={"SQL_TLS_TRUSTSTORE_PATH","SQL_TLS_TRUSTSTORE_PASSWORD"})
    void blankConfigurationIsRejected(String key) throws Exception {
        rejects(TlsFixtures.tls(new MockEnvironment(),TlsFixtures.store(temp)).withProperty(key," "));
    }

    @Test void relativePathIsRejected() {
        rejects(TlsFixtures.tls(new MockEnvironment(),Path.of("relative.jks")));
    }

    @Test void absentFileIsRejected() {
        rejects(TlsFixtures.tls(new MockEnvironment(),temp.resolve("absent.jks")));
    }

    @Test void directoryIsNotATrustStore() {
        rejects(TlsFixtures.tls(new MockEnvironment(),temp));
    }

    @Test void unreadableFileIsRejectedWithoutChangingOperatorPermissions() throws Exception {
        Path path = TlsFixtures.store(temp);
        try (var files = mockStatic(Files.class,CALLS_REAL_METHODS)) {
            files.when(() -> Files.isReadable(path)).thenReturn(false);
            rejects(TlsFixtures.tls(new MockEnvironment(),path));
        }
    }

    @Test void fileInsideRepositoryIsRejected() throws Exception {
        Path directory=Files.createTempDirectory(Path.of("target"),"tls-forbidden-");
        Path path=TlsFixtures.store(directory);
        try { rejects(TlsFixtures.tls(new MockEnvironment(),path)); }
        finally { Files.delete(path);Files.delete(directory); }
    }

    @ParameterizedTest @ValueSource(strings={"plain","truncated","pkcs12"})
    void actualJksFormatIsRequiredEvenWhenJdkSupportsDualFormat(String mode) throws Exception {
        Path path=temp.resolve("invalid.jks");
        if (mode.equals("plain")) Files.writeString(path,"NOT_A_KEYSTORE");
        else if (mode.equals("truncated")) Files.write(path,new byte[]{(byte)0xfe,(byte)0xed,(byte)0xfe,(byte)0xed,0,0,0,2});
        else {
            var store=KeyStore.getInstance("PKCS12");store.load(null,TlsFixtures.STORE_PASSWORD.toCharArray());
            try(var output=Files.newOutputStream(path)){store.store(output,TlsFixtures.STORE_PASSWORD.toCharArray());}
        }
        rejects(TlsFixtures.tls(new MockEnvironment(),path));
    }

    @Test void incorrectIntegrityPasswordIsRejected(CapturedOutput output) throws Exception {
        rejects(TlsFixtures.tls(new MockEnvironment(),TlsFixtures.store(temp))
                .withProperty("SQL_TLS_TRUSTSTORE_PASSWORD","WRONG_TLS_CANARY"));
        assertThat(output).doesNotContain("WRONG_TLS_CANARY",TlsFixtures.STORE_PASSWORD);
    }

    @ParameterizedTest @ValueSource(strings={"SQL_TLS_TRUSTSTORE_PATH","SQL_TLS_TRUSTSTORE_PASSWORD"})
    void resolverFailuresNeverEscapeWithTheirMessage(String key,CapturedOutput output) throws Exception {
        rejects(TlsFixtures.tls(new MockEnvironment(),TlsFixtures.store(temp)).withProperty(key,"${UNRESOLVED_TLS_CANARY}"));
        assertThat(output).doesNotContain("UNRESOLVED_TLS_CANARY");
    }

    @Test void loadingSyntheticJksDoesNotCreateConnectionOrChangeGlobalTrust() throws Exception {
        String previous=System.getProperty("javax.net.ssl.trustStore");
        Path path=TlsFixtures.store(temp);
        try(var driver=new TlsFixtures.CaptureDriver()) {
            var tls=SqlTlsSettings.from(TlsFixtures.tls(new MockEnvironment(),path));
            assertThat(tls.toString()).isEqualTo("SqlTlsSettings[REDACTED]");
            assertThat(driver.urls).isEmpty();
            // Mensaje cerrado: no imprimir un posible valor externo al proceso de pruebas.
            assertThat(java.util.Objects.equals(previous,System.getProperty("javax.net.ssl.trustStore"))).isTrue();
        }
    }

    @Test void unitProfileDoesNotResolveOrOpenTrustStore() {
        var environment=new MockEnvironment();environment.setActiveProfiles("auth-unit");
        environment.withProperty("SQL_TLS_TRUSTSTORE_PATH","${UNRESOLVED_TLS_CANARY}")
                .withProperty("SQL_TLS_TRUSTSTORE_PASSWORD","${UNRESOLVED_TLS_CANARY}")
                .withProperty("AUTH_DB_URL","jdbc:unusable").withProperty("AUTH_DB_PASSWORD",TlsFixtures.SQL_PASSWORD);
        try(var tls=mockStatic(SqlTlsSettings.class);var identity=mockStatic(DatabaseIdentity.class)) {
            new ApplicationContextRunner().withUserConfiguration(AuthDataSourceConfig.class,ReportConfig.class)
                    .withBean(AutenticacionService.class,() -> mock(AutenticacionService.class))
                    .withInitializer(c -> c.setEnvironment(environment))
                    .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(javax.sql.DataSource.class));
            tls.verifyNoInteractions();identity.verifyNoInteractions();
        }
    }

    @ParameterizedTest @ValueSource(strings={"auth-db","auth-integration"})
    void authMissingTlsFailsBeforeIdentityAndOpening(String profile) throws Exception {
        var environment=TlsFixtures.auth(TlsFixtures.store(temp),profile.equals("auth-integration"))
                .withProperty("SQL_TLS_TRUSTSTORE_PATH","");
        try(var identity=mockStatic(DatabaseIdentity.class)) {
            assertThatIllegalStateException().isThrownBy(() -> new AuthDataSourceConfig().authDataSource(environment))
                    .withMessage("Configuracion TLS SQL no autorizada o incompleta").withNoCause();
            identity.verifyNoInteractions();
        }
    }

    @Test void unapprovedReportsNeverReadTlsEvenIfEnvironmentSaysVerified() {
        var environment=new MockEnvironment();environment.setActiveProfiles("auth-db");
        environment.withProperty("REPORTS_READER_MODE","verified").withProperty("REPORTS_CONTRACT_ID","unapproved")
                .withProperty("SQL_TLS_TRUSTSTORE_PASSWORD","${UNRESOLVED_TLS_CANARY}");
        try(var tls=mockStatic(SqlTlsSettings.class)) {
            new ApplicationContextRunner().withUserConfiguration(ReportConfig.class)
                    .withBean(AutenticacionService.class,() -> mock(AutenticacionService.class))
                    .withInitializer(c -> c.setEnvironment(environment)).run(c -> {
                        assertThat(c).hasNotFailed().doesNotHaveBean("reportsDataSource");
                        assertThat(c.getBean(CommercialReportReader.class)).isInstanceOf(UnconfiguredCommercialReader.class);
                    });
            tls.verifyNoInteractions();
        }
    }

    @Test void reportsMissingTlsFailsBeforePreparingDatasource() {
        var environment=TlsFixtures.reports(temp.resolve("absent.jks"));
        assertThatIllegalStateException().isThrownBy(() -> ReportsDatabaseSettings.from(environment))
                .withMessage("Configuracion SQL Reportes no autorizada o incompleta").withNoCause();
    }
}
