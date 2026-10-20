package pe.edu.cibertec.ferrestock.authreportes.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.*;

public final class AutenticacionService {
    private final UsuarioRepository users; private final SesionRepository sessions;
    private final PasswordEncoder passwords; private final OpaqueTokens tokens;
    private final AuthTransactions tx; private final LoginLimiter limiter; private final String dummyHash;
    public AutenticacionService(UsuarioRepository users, SesionRepository sessions, PasswordEncoder passwords,
                                OpaqueTokens tokens, AuthTransactions tx, LoginLimiter limiter) {
        this.users=users;this.sessions=sessions;this.passwords=passwords;this.tokens=tokens;this.tx=tx;this.limiter=limiter;
        dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public LoginResult login(Login input, String source) {
        String login;
        try {login=LoginNormalizer.normalize(input.login());}
        catch(IllegalArgumentException ex) {throw ApiProblem.unauthorized();}
        limiter.attempt(login,source);limiter.acquire();
        Usuario candidate;
        try {
            try {candidate=users.findByLogin(login).orElse(null);}
            catch(RuntimeException ex) {throw ApiProblem.unavailable();}
            boolean valid=passwords.matches(input.password(),candidate==null?dummyHash:candidate.passwordHash());
            if(!valid || candidate==null || candidate.estado()!=EstadoUsuario.ACTIVO) throw ApiProblem.unauthorized();
        } finally {limiter.release();}
        // KDF fuera de bloqueo; volver a comprobar hash, estado y version bajo UPDLOCK.
        return tx.run(() -> {
            Usuario current=users.findByIdForUpdate(candidate.id()).orElseThrow(ApiProblem::unauthorized);
            if(current.estado()!=EstadoUsuario.ACTIVO || current.versionSeguridad()!=candidate.versionSeguridad()
                    || !current.passwordHash().equals(candidate.passwordHash())) throw ApiProblem.unauthorized();
            var now=users.databaseNow();var expires=now.plus(Duration.ofHours(8));var token=tokens.issue();
            sessions.insert(new Sesion(null,current.id(),token.hash(),current.versionSeguridad(),now,expires,null,null));
            return new LoginResult(token.value(),"Bearer",expires,UserView.of(current));
        }); // El retorno externo ocurre despues del COMMIT de TransactionTemplate.
    }
    public AuthPrincipal authenticate(String value) {
        byte[] hash;
        try {hash=tokens.hash(value);}catch(IllegalArgumentException ex){throw ApiProblem.unauthorized();}
        try {
            return tx.run(() -> {
                Sesion first=sessions.findActiveByHash(hash).orElseThrow(ApiProblem::unauthorized);
                Usuario user=users.findByIdForUpdate(first.usuarioId()).orElseThrow(ApiProblem::unauthorized);
                Sesion current=sessions.findActiveByHash(hash).orElseThrow(ApiProblem::unauthorized);
                if(user.estado()!=EstadoUsuario.ACTIVO || current.usuarioId()!=user.id()
                        || current.versionSeguridad()!=user.versionSeguridad()) throw ApiProblem.unauthorized();
                return new AuthPrincipal(user.id(),current.id(),user.versionSeguridad(),user.rol());
            });
        } finally {Arrays.fill(hash,(byte)0);}
    }
    /** Revalida tambien en servicios para no confiar en rol o identidad enviados por cliente. */
    public Usuario requireCurrent(AuthPrincipal principal, boolean admin) {
        if(principal==null) throw ApiProblem.unauthorized();
        Usuario user=users.findByIdForUpdate(principal.usuarioId()).orElseThrow(ApiProblem::unauthorized);
        if(user.estado()!=EstadoUsuario.ACTIVO || user.versionSeguridad()!=principal.versionSeguridad()) throw ApiProblem.unauthorized();
        if(admin && user.rol()!=Rol.ADMINISTRADOR) throw new ApiProblem(403,"ACCESO_DENEGADO");
        return user;
    }
    public UserView me(AuthPrincipal principal) {return tx.run(() -> UserView.of(requireCurrent(principal,false)));}
    public void requireAdministrator(AuthPrincipal principal) {tx.run(() -> {requireCurrent(principal,true);return null;});}
    public void logout(AuthPrincipal principal) {
        tx.run(() -> {requireCurrent(principal,false);sessions.revoke(principal.sesionId(),principal.usuarioId(),MotivoRevocacion.LOGOUT);return null;});
    }
}
