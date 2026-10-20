package pe.edu.cibertec.ferrestock.authreportes.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.OpaqueTokens;
import pe.edu.cibertec.ferrestock.authreportes.service.*;

@Configuration(proxyBeanMethods=false)
@Profile({"auth-db","auth-integration"})
public class AuthServiceConfig {
    @Bean AuthTransactions authTransactions(@Qualifier("authTransactionManager") PlatformTransactionManager manager){return new AuthTransactions(manager);}
    @Bean LoginLimiter loginLimiter(){return new LoginLimiter(Clock.systemUTC());}
    @Bean AutenticacionService autenticacionService(UsuarioRepository u,SesionRepository s,PasswordEncoder p,OpaqueTokens t,AuthTransactions tx,LoginLimiter limiter){return new AutenticacionService(u,s,p,t,tx,limiter);}
    @Bean UsuarioService usuarioService(UsuarioRepository u,RolRepository r,SesionRepository s,PasswordEncoder p,AuthTransactions tx,AutenticacionService a){return new UsuarioService(u,r,s,p,tx,a);}
}
