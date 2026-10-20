package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.jdbc.*;

@Configuration(proxyBeanMethods = false)
@Profile({"auth-db", "auth-integration"})
public class AuthRepositoryConfig {
    @Bean RolRepository rolRepository(@Qualifier("authJdbc") NamedParameterJdbcTemplate jdbc) { return new JdbcRolRepository(jdbc); }
    @Bean UsuarioRepository usuarioRepository(@Qualifier("authJdbc") NamedParameterJdbcTemplate jdbc) { return new JdbcUsuarioRepository(jdbc); }
    @Bean SesionRepository sesionRepository(@Qualifier("authJdbc") NamedParameterJdbcTemplate jdbc) { return new JdbcSesionRepository(jdbc); }
}
