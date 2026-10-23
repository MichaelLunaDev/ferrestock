package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
@Profile({"auth-db", "auth-integration"})
public class AuthDataSourceConfig {
    @Bean
    DataSource authDataSource(Environment env) {
        var settings = AuthDatabaseSettings.from(env);
        // DEV-LOCAL (22/10/2026): sin JKS provisionado, trustServerCertificate=true
        // usa una conexion simple sin StrictSqlDataSource/SqlTlsSettings (que exigen JKS).
        // Con trustServerCertificate=false se mantiene el camino estricto original.
        DataSource source;
        if (settings.url().contains("trustServerCertificate=true")) {
            var simple = new DriverManagerDataSource();
            simple.setUrl(settings.url() + ";loginTimeout=10");
            simple.setUsername(settings.user());
            simple.setPassword(settings.password());
            source = simple;
        } else {
            source = new StrictSqlDataSource(settings.url(), settings.user(), settings.password(), SqlTlsSettings.from(env));
        }
        DatabaseIdentity.verify(source, settings);
        return source;
    }

    @Bean
    NamedParameterJdbcTemplate authJdbc(@Qualifier("authDataSource") DataSource source) {
        return new NamedParameterJdbcTemplate(source);
    }

    @Bean
    JdbcTransactionManager authTransactionManager(@Qualifier("authDataSource") DataSource source) {
        return new JdbcTransactionManager(source);
    }
}
