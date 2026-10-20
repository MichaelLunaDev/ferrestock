package pe.edu.cibertec.ferrestock.authreportes.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regresiones integradas: las dos condiciones antes aceptadas ahora deben rechazarse. */
class PreparationReviewTest {
    @Test void rejectsNonConnectionResource() throws Exception {
        var source=mock(DataSource.class);
        var jdbc=new NamedParameterJdbcTemplate(source);
        var method=Class.forName("pe.edu.cibertec.ferrestock.authreportes.repository.jdbc.JdbcSupport")
                .getDeclaredMethod("requireTransaction",org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations.class);
        method.setAccessible(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(source,new Object());
        try {
            // La guardia corregida debe rechazar un Object sin Connection.
            assertThatThrownBy(() -> method.invoke(null,jdbc)).hasCauseInstanceOf(IllegalStateException.class);
            verifyNoInteractions(source);
        } finally {
            TransactionSynchronizationManager.unbindResource(source);
            TransactionSynchronizationManager.clear();
        }
    }

    @Test void authUnitExcludesExplicitSqlProfile() {
        var env=new MockEnvironment()
                .withProperty("AUTH_DB_URL","jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_auth_dev;encrypt=true;trustServerCertificate=false")
                .withProperty("AUTH_DB_USER","ferrestock_auth_app").withProperty("AUTH_DB_PASSWORD","SYNTHETIC_ONLY");
        env.setActiveProfiles("auth-unit","auth-db");
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env));
    }
}
