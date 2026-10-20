package pe.edu.cibertec.ferrestock.authreportes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class ReportsDatabaseSettingsTest {
    @TempDir Path temp;
    @Test void runtimeStillHasPendingReaderAndNoCommercialDataSource(){
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(ReportConfig.class)
            .withBean(pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService.class,
                () -> org.mockito.Mockito.mock(pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService.class))
            .withInitializer(c -> c.setEnvironment(env()))
            .run(c -> {
                assertThat(c).doesNotHaveBean(javax.sql.DataSource.class);
                assertThat(c.getBean(pe.edu.cibertec.ferrestock.authreportes.reportes.CommercialReportReader.class))
                    .isInstanceOf(pe.edu.cibertec.ferrestock.authreportes.reportes.UnconfiguredCommercialReader.class);
            });
    }
    MockEnvironment env(){var env=new MockEnvironment();env.setActiveProfiles("auth-db");return env
        .withProperty("REPORTS_DB_URL","jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=false")
        .withProperty("REPORTS_DB_USER","ferrestock_report_reader").withProperty("REPORTS_DB_PASSWORD","SECRET_CANARY");}
    @Test void preparedSourceIsIndependentAndNeverOpened() throws Exception {
        var settings=ReportsDatabaseSettings.from(TlsFixtures.tls(env(),TlsFixtures.store(temp)));
        assertThat(settings.toString()).doesNotContain("SECRET_CANARY","jdbc:");
        try(var driver=new TlsFixtures.CaptureDriver()) {
            var ds=settings.prepareDataSource();assertThat(driver.urls).isEmpty();
            try(var ignored=ds.getConnection()) { }
            assertThat(driver.properties.getFirst().getProperty("user")).isEqualTo("ferrestock_report_reader");
            assertThat(driver.urls.getFirst()).endsWith(";trustServerCertificate=false;loginTimeout=10");
        }
    }
    @ParameterizedTest @ValueSource(strings={"sa","ferrestock_auth_app","ferrestock_auth_migrator","ferrestock_auth_test_app"})
    void forbiddenIdentity(String user){assertThatThrownBy(() -> ReportsDatabaseSettings.from(env().withProperty("REPORTS_DB_USER",user))).hasNoCause().hasMessage("Configuracion SQL Reportes no autorizada o incompleta");}
    @ParameterizedTest @ValueSource(strings={"auth-unit","auth-integration"}) void isolatedProfiles(String profile){
        var env=env();env.setActiveProfiles("auth-db",profile);assertThatIllegalStateException().isThrownBy(() -> ReportsDatabaseSettings.from(env));
    }
    @Test void missingCredentialsDoNotFallBackToAuth(){assertThatIllegalStateException().isThrownBy(() -> ReportsDatabaseSettings.from(env().withProperty("REPORTS_DB_PASSWORD"," ")));}
    @Test void tlsAndDestinationAreClosed(){var env=env();env.withProperty("REPORTS_DB_URL",env.getProperty("REPORTS_DB_URL")+";trustServerCertificate=true");assertThatIllegalStateException().isThrownBy(() -> ReportsDatabaseSettings.from(env));}
    @Test void propertyResolutionFailureIsSanitized(){assertThatThrownBy(() -> ReportsDatabaseSettings.from(env().withProperty("REPORTS_DB_PASSWORD","${UNKNOWN_SECRET_CANARY}"))).hasNoCause().hasMessage("Configuracion SQL Reportes no autorizada o incompleta");}
}
