package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.cibertec.ferrestock.authreportes.security.OpaqueTokens;
import pe.edu.cibertec.ferrestock.authreportes.security.VersionedPasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class CryptoConfig {
    @Bean PasswordEncoder passwordEncoder() { return new VersionedPasswordEncoder(); }
    @Bean OpaqueTokens opaqueTokens() { return new OpaqueTokens(); }
}
