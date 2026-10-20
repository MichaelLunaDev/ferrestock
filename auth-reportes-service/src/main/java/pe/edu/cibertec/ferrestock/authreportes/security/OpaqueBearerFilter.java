package pe.edu.cibertec.ferrestock.authreportes.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;

public final class OpaqueBearerFilter extends OncePerRequestFilter {
    private final AutenticacionService auth;
    public OpaqueBearerFilter(AutenticacionService auth){this.auth=auth;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return ("GET".equals(request.getMethod()) && "/api/health".equals(path))
            || ("POST".equals(request.getMethod()) && "/api/auth/login".equals(path));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var headers=Collections.list(request.getHeaders("Authorization"));
        if(!headers.isEmpty()) {
            try {
                if(headers.size()!=1) throw ApiProblem.unauthorized();
                String header=headers.getFirst();
                if(header.length()!=50 || !header.regionMatches(true,0,"Bearer ",0,7)) throw ApiProblem.unauthorized();
                AuthPrincipal principal=auth.authenticate(header.substring(7));
                var context=SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_"+principal.rol().name()))));
                SecurityContextHolder.setContext(context);
            } catch(ApiProblem ex) {SecurityContextHolder.clearContext();writeError(response,ex.status(),ex.code());return;}
            catch(RuntimeException ex){SecurityContextHolder.clearContext();writeError(response,503,"SERVICIO_NO_DISPONIBLE");return;}
        }
        try {chain.doFilter(request,response);} finally {SecurityContextHolder.clearContext();}
    }
    static void writeError(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        if(status==401) response.setHeader("WWW-Authenticate","Bearer");
        response.getWriter().write("{\"code\":\""+code+"\"}");
    }
}
