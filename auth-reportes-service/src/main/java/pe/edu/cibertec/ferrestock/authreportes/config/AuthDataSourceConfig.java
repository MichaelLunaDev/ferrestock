package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@Profile({"auth-db", "auth-integration"})
public class AuthDataSourceConfig {
    @Bean
    DataSource authDataSource(Environment env) {
        var settings = AuthDatabaseSettings.from(env);
        // Preparacion sin pool: conexiones solo al activar expresamente el perfil SQL.
        var source = new DriverManagerDataSource();
        source.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        source.setUrl(settings.url() + ";loginTimeout=10");
        source.setUsername(settings.user());
        source.setPassword(settings.password());
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
