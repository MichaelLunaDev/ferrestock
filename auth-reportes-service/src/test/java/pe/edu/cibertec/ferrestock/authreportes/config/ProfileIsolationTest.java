package pe.edu.cibertec.ferrestock.authreportes.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileIsolationTest {
    private static MockEnvironment environment(String... profiles) {
        var env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        // Configuracion SQL completa sintética: no permite que falten variables ocultando M1.
        for (boolean test : new boolean[]{false,true}) {
            String prefix = test ? "AUTH_TEST_DB_" : "AUTH_DB_";
            env.withProperty(prefix+"URL", "jdbc:sqlserver://127.0.0.1:14333;databaseName="
                    +(test ? "ferrestock_auth_test" : "ferrestock_auth_dev")+";encrypt=true;trustServerCertificate=false");
            env.withProperty(prefix+"USER", test ? "ferrestock_auth_test_app" : "ferrestock_auth_app");
            env.withProperty(prefix+"PASSWORD", "A1_PROFILE_CANARY");
        }
        return env;
    }

    @ParameterizedTest
    @ValueSource(strings={"auth-unit,auth-db", "auth-unit,auth-integration", "auth-db,auth-integration",
            "auth-unit,auth-db,auth-integration"})
    void invalidCombinationFailsBeforeIdentityOrConnection(String combination) {
        var env = environment(combination.split(","));
        assertThatIllegalStateException().isThrownBy(() -> AuthDatabaseSettings.from(env))
                .withMessage("Seleccionar un unico perfil SQL Auth").withNoCause();
        try (var identity = mockStatic(DatabaseIdentity.class)) {
            new ApplicationContextRunner().withUserConfiguration(AuthDataSourceConfig.class,AuthRepositoryConfig.class)
                    .withInitializer(context -> context.setEnvironment(env))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasRootCauseMessage("Seleccionar un unico perfil SQL Auth");
                    });
            identity.verifyNoInteractions(); // verify contiene el unico getConnection del arranque SQL.
        }
    }

    @Test void unitProfileHasNoDatasourceDespiteSqlVariables() {
        try (var identity = mockStatic(DatabaseIdentity.class)) {
            new ApplicationContextRunner().withUserConfiguration(AuthDataSourceConfig.class,AuthRepositoryConfig.class)
                    .withInitializer(context -> context.setEnvironment(environment("auth-unit")))
                    .run(context -> {
                        assertThat(context).hasNotFailed().doesNotHaveBean(DataSource.class);
                        assertThat(context).doesNotHaveBean("authJdbc").doesNotHaveBean("authTransactionManager")
                                .doesNotHaveBean("usuarioRepository").doesNotHaveBean("sesionRepository");
                    });
            identity.verifyNoInteractions();
        }
    }
}
