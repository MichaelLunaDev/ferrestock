package pe.edu.cibertec.ferrestock.authreportes.config;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import pe.edu.cibertec.ferrestock.authreportes.reportes.*;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommercialWiringTest {
    @ParameterizedTest @ValueSource(strings={"auth-db","auth-unit","auth-integration"})
    void environmentCannotApproveUnverifiedContract(String profile){
        var environment=new MockEnvironment();environment.setActiveProfiles(profile);
        environment.withProperty("REPORTS_READER_MODE","verified").withProperty("REPORTS_CONTRACT_ID","unverified")
            .withProperty("REPORTS_DB_PASSWORD","${UNRESOLVED_CANARY}");
        assertThat(ReviewedCommercialContracts.schema(environment)).isEmpty();
        new ApplicationContextRunner().withUserConfiguration(ReportConfig.class)
            .withBean(AutenticacionService.class,() -> mock(AutenticacionService.class))
            .withInitializer(c -> c.setEnvironment(environment)).run(c -> {
                assertThat(c).doesNotHaveBean(DataSource.class);
                if(!profile.equals("auth-unit"))assertThat(c.getBean(CommercialReportReader.class)).isInstanceOf(UnconfiguredCommercialReader.class);
            });
    }
    @Test void preparedWiringUsesOnlyQualifiedCommercialSourceWithoutOpeningIt(){
        var reports=mock(DataSource.class);var auth=mock(DataSource.class);
        var env=new MockEnvironment();env.setActiveProfiles("auth-db");
        new ApplicationContextRunner().withUserConfiguration(ReportConfig.class)
            .withBean(AutenticacionService.class,() -> mock(AutenticacionService.class))
            .withBean("authDataSource",DataSource.class,() -> auth)
            .withBean("reportsDataSource",DataSource.class,() -> reports)
            .withBean("reportsCommercialSql",CandidateCommercialSql.class,() -> new CandidateCommercialSql("fixture"))
            .withInitializer(c -> c.setEnvironment(env)).run(c ->
                assertThat(c.getBean(CommercialReportReader.class)).isInstanceOf(JdbcCommercialReportReader.class));
        verifyNoInteractions(reports,auth);
    }
}
