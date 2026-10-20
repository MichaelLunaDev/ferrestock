package pe.edu.cibertec.ferrestock.authreportes.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Pure opt-in checks: these tests never construct a datasource or run the integration fixture. */
class AuthSqlGateTest {
    @Test void missingApprovalFailsBeforeSqlConfiguration() {
        assertThatThrownBy(() -> AuthSqlIT.checkApproval(Map.of())).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"false", "TRUE", "true ", ""})
    void ambiguousApprovalRejected(String approval) {
        assertThatThrownBy(() -> AuthSqlIT.checkApproval(Map.of("FERRESTOCK_AUTH_TEST_FIXTURES_APPROVED", approval)))
                .isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"auth-db", "auth-unit", "auth-integration,auth-db", ""})
    void otherProfilesRejectedEvenWithApproval(String profile) {
        assertThatThrownBy(() -> AuthSqlIT.checkApproval(Map.of("FERRESTOCK_AUTH_TEST_FIXTURES_APPROVED", "true",
                "SPRING_PROFILES_ACTIVE", profile))).isInstanceOf(IllegalStateException.class);
    }
    @Test void explicitTestApprovalAcceptedWithoutConnecting() {
        assertThatNoException().isThrownBy(() -> AuthSqlIT.checkApproval(Map.of("FERRESTOCK_AUTH_TEST_FIXTURES_APPROVED", "true",
                "SPRING_PROFILES_ACTIVE", "auth-integration")));
    }
}
