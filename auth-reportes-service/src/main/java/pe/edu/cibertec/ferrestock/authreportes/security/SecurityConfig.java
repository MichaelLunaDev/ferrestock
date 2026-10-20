package pe.edu.cibertec.ferrestock.authreportes.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<AutenticacionService> provider) throws Exception {
        AutenticacionService auth=provider.getIfAvailable();
        boolean enabled=auth!=null;
        if(enabled) http.addFilterBefore(new OpaqueBearerFilter(auth),AnonymousAuthenticationFilter.class);
        return http
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    if(enabled) {
                        authorize.requestMatchers(HttpMethod.POST,"/api/auth/login").permitAll()
                            .requestMatchers(HttpMethod.POST,"/api/auth/logout").authenticated()
                            .requestMatchers(HttpMethod.GET,"/api/auth/me").authenticated()
                            .requestMatchers(HttpMethod.GET,"/api/usuarios","/api/usuarios/{id}").hasRole("ADMINISTRADOR")
                            .requestMatchers(HttpMethod.POST,"/api/usuarios").hasRole("ADMINISTRADOR")
                            .requestMatchers(HttpMethod.PUT,"/api/usuarios/{id}","/api/usuarios/{id}/password","/api/usuarios/{id}/rol","/api/usuarios/{id}/estado").hasRole("ADMINISTRADOR")
                            .requestMatchers(HttpMethod.GET,"/api/reportes/ventas","/api/reportes/productos-mas-vendidos","/api/reportes/stock-bajo").hasRole("ADMINISTRADOR");
                    }
                    authorize.anyRequest().denyAll();
                })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Bearer explicito, sin cookies de autenticacion ni HttpSession.
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            if(enabled) OpaqueBearerFilter.writeError(response,401,"AUTENTICACION_REQUERIDA");
                            else response.setStatus(403);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            if(enabled) OpaqueBearerFilter.writeError(response,403,"ACCESO_DENEGADO");
                            else response.setStatus(403);
                        }))
                .build();
    }
}
