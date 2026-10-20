package pe.edu.cibertec.ferrestock.authreportes.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class StrictSqlDataSourceTest {
    @TempDir Path temp;

    @ParameterizedTest @ValueSource(strings={"auth-db","auth-integration","reports"})
    void propertiesReachDriverConnectWithClosedUrlAndExactIdentity(String mode) throws Exception {
        Path store=TlsFixtures.store(temp);
        try(var driver=new TlsFixtures.CaptureDriver()) {
            var source=mode.equals("reports")
                    ? ReportsDatabaseSettings.from(TlsFixtures.reports(store)).prepareDataSource()
                    : new AuthDataSourceConfig().authDataSource(TlsFixtures.auth(store,mode.equals("auth-integration")));
            assertThat(driver.urls).hasSize(mode.equals("reports") ? 0 : 1);
            if(mode.equals("reports"))try(var ignored=source.getConnection()){ }
            String db=mode.equals("reports") ? "ferrestock_ventas" : "ferrestock_auth_"+(mode.equals("auth-db") ? "dev" : "test");
            String user=mode.equals("reports") ? "ferrestock_report_reader" : mode.equals("auth-db") ? "ferrestock_auth_app" : "ferrestock_auth_test_app";
            assertThat(driver.urls.getFirst()).isEqualTo("jdbc:sqlserver://127.0.0.1:14333;databaseName="+db+";encrypt=true;trustServerCertificate=false;loginTimeout=10");
            var properties=driver.properties.getFirst();
            assertThat(properties.stringPropertyNames()).containsExactlyInAnyOrder("user","password","trustStore","trustStorePassword","trustStoreType");
            assertThat(properties.getProperty("user")).isEqualTo(user);
            assertThat(properties.getProperty("password")).isEqualTo(TlsFixtures.SQL_PASSWORD);
            assertThat(properties.getProperty("trustStore")).isEqualTo(store.toString());
            assertThat(properties.getProperty("trustStorePassword")).isEqualTo(TlsFixtures.STORE_PASSWORD);
            assertThat(properties.getProperty("trustStoreType")).isEqualTo("JKS");
        }
    }

    @Test void resolvedMicrosoftDriverRecognizesTrustPropertiesWithoutConnecting() throws Exception {
        var driver=(Driver)Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver").getDeclaredConstructor().newInstance();
        var info=Arrays.asList(driver.getPropertyInfo("jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_auth_test;encrypt=true;trustServerCertificate=false;loginTimeout=10",new Properties()));
        assertThat(info.stream().map(p -> p.name)).contains("trustStore","trustStorePassword","trustStoreType");
        assertThat(info.stream().filter(p -> p.name.equals("encrypt")).findFirst().orElseThrow().value).isEqualTo("true");
        assertThat(info.stream().filter(p -> p.name.equals("trustServerCertificate")).findFirst().orElseThrow().value).isEqualTo("false");
    }

    @Test void eachConnectionGetsFreshPropertiesAndCannotCorruptNextConnection() throws Exception {
        Path store=TlsFixtures.store(temp);
        try(var driver=new TlsFixtures.CaptureDriver()) {
            driver.mutateProperties=true;
            var source=ReportsDatabaseSettings.from(TlsFixtures.reports(store)).prepareDataSource();
            try(var ignored=source.getConnection()){ }
            try(var ignored=source.getConnection()){ }
            assertThat(driver.properties).hasSize(2);
            for(var properties:driver.properties) {
                assertThat(properties.getProperty("trustStorePassword")).isEqualTo(TlsFixtures.STORE_PASSWORD);
                assertThat(properties.getProperty("user")).isEqualTo("ferrestock_report_reader");
                assertThat(properties.getProperty("password")).isEqualTo(TlsFixtures.SQL_PASSWORD);
            }
        }
    }

    @Test void sourcesKeepSeparateSqlCredentialsAndSameExplicitTrustPolicy() throws Exception {
        Path store=TlsFixtures.store(temp);
        try(var driver=new TlsFixtures.CaptureDriver()) {
            var auth=new AuthDataSourceConfig().authDataSource(TlsFixtures.auth(store,false));
            var reports=ReportsDatabaseSettings.from(TlsFixtures.reports(store)
                    .withProperty("REPORTS_DB_PASSWORD","REPORTS_ONLY_CANARY")).prepareDataSource();
            assertThat(auth).isNotSameAs(reports);
            try(var ignored=reports.getConnection()){ }
            assertThat(driver.properties.get(0).getProperty("password")).isEqualTo(TlsFixtures.SQL_PASSWORD);
            assertThat(driver.properties.get(1).getProperty("password")).isEqualTo("REPORTS_ONLY_CANARY");
            assertThat(driver.properties.get(0).getProperty("user")).isEqualTo("ferrestock_auth_app");
            assertThat(driver.properties.get(1).getProperty("user")).isEqualTo("ferrestock_report_reader");
            assertThat(driver.properties.get(0).getProperty("trustStore")).isEqualTo(driver.properties.get(1).getProperty("trustStore"));
        }
    }

    @Test void secretsAreNotExposedByDatasourceToStringUnwrapOrDriverFailure(CapturedOutput output) throws Exception {
        Path store=TlsFixtures.store(temp);
        var settings=ReportsDatabaseSettings.from(TlsFixtures.reports(store));
        var source=settings.prepareDataSource();
        assertThat(source).isNotInstanceOf(DriverManagerDataSource.class);
        assertThat(source.isWrapperFor(DriverManagerDataSource.class)).isFalse();
        assertThatThrownBy(() -> source.unwrap(DriverManagerDataSource.class)).isInstanceOf(SQLException.class).hasNoCause();
        assertThat(source.toString()+settings.toString()).doesNotContain(TlsFixtures.SQL_PASSWORD,TlsFixtures.STORE_PASSWORD,store.toString(),"jdbc:");
        try(var driver=new TlsFixtures.CaptureDriver()) {
            driver.fail=true;
            var failure=catchThrowable(source::getConnection);
            assertThat(failure).isInstanceOf(SQLException.class).hasMessage("Conexion SQL rechazada").hasNoCause();
            assertThat(failure.getSuppressed()).isEmpty();
        }
        assertThat(output).doesNotContain(TlsFixtures.SQL_PASSWORD,TlsFixtures.STORE_PASSWORD,store.toString());
    }

    @Test void alternativeCredentialsAreRejectedWithoutCallingDriver() throws Exception {
        try(var driver=new TlsFixtures.CaptureDriver()) {
            var source=ReportsDatabaseSettings.from(TlsFixtures.reports(TlsFixtures.store(temp))).prepareDataSource();
            assertThatThrownBy(() -> source.getConnection("sa","ALTERNATE_SECRET_CANARY"))
                    .isInstanceOf(SQLException.class).hasMessage("Identidad SQL alternativa no autorizada").hasNoCause();
            assertThat(driver.urls).isEmpty();
        }
    }

    @Test void changedStoreFailsClosedBeforeDriverConnect() throws Exception {
        Path store=TlsFixtures.store(temp);
        var source=ReportsDatabaseSettings.from(TlsFixtures.reports(store)).prepareDataSource();
        Files.writeString(store,"CORRUPTED_FIXTURE");
        try(var driver=new TlsFixtures.CaptureDriver()) {
            assertThatThrownBy(source::getConnection).isInstanceOf(SQLException.class)
                    .hasMessage("Conexion SQL rechazada").hasNoCause();
            assertThat(driver.urls).isEmpty();
        }
    }
}
