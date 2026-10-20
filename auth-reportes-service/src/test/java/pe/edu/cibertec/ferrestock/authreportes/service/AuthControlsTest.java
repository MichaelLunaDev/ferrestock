package pe.edu.cibertec.ferrestock.authreportes.service;

import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Dobles locales; no demuestra bloqueos, rollback ni COMMIT en SQL Server. */
class AuthControlsTest {
    UsuarioRepository users=mock(UsuarioRepository.class);SesionRepository sessions=mock(SesionRepository.class);
    RolRepository roles=mock(RolRepository.class);PasswordEncoder passwords=mock(PasswordEncoder.class);
    PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    Instant now=Instant.parse("2026-01-01T00:00:00Z");
    AuthTransactions tx;AutenticacionService auth;UsuarioService administration;
    AuthPrincipal principal=new AuthPrincipal(1,10,1,Rol.ADMINISTRADOR);
    Usuario user(long id,long version,Rol role,EstadoUsuario state){return new Usuario(id,"user"+id,"Nombre","hash",state,role,version,now,now,null,null);}
    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(passwords.encode(any())).thenReturn("encoded-hash");
        tx=new AuthTransactions(manager);
        auth=new AutenticacionService(users,sessions,passwords,new OpaqueTokens(),tx,new LoginLimiter(Clock.fixed(now,ZoneOffset.UTC)));
        administration=new UsuarioService(users,roles,sessions,passwords,tx,auth);
    }
    @Test void loginRejectsChangeBetweenHashCheckAndLockedRow(){
        when(users.findByLogin("user1")).thenReturn(Optional.of(user(1,1,Rol.VENDEDOR,EstadoUsuario.ACTIVO)));
        when(passwords.matches("a-long-password","hash")).thenReturn(true);
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,2,Rol.VENDEDOR,EstadoUsuario.ACTIVO)));
        assertThatThrownBy(() -> auth.login(new Login("USER1","a-long-password"),"local")).isInstanceOf(ApiProblem.class).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verify(sessions,never()).insert(any());verify(manager).rollback(any());
    }
    @Test void loginPersistsOnlyHashAndReturnsAfterCommit(){
        var current=user(1,1,Rol.VENDEDOR,EstadoUsuario.ACTIVO);
        when(users.findByLogin("user1")).thenReturn(Optional.of(current));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(current));
        when(passwords.matches("a-long-password","hash")).thenReturn(true);when(users.databaseNow()).thenReturn(now);
        LoginResult result=auth.login(new Login("user1","a-long-password"),"local");
        var capture=org.mockito.ArgumentCaptor.forClass(Sesion.class);verify(sessions).insert(capture.capture());verify(manager).commit(any());
        assertThat(capture.getValue().tokenHash()).containsExactly(new OpaqueTokens().hash(result.accessToken()));
        assertThat(result.expiresAt()).isEqualTo(now.plusSeconds(8*3600));
        assertThat(result.toString()).doesNotContain(result.accessToken(),"hash");
    }
    @Test void cannotDemoteLastActiveAdministrator(){
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,1,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO)));
        assertThatThrownBy(() -> administration.role(principal,1,new RoleChange(Rol.VENDEDOR,1))).hasMessage("ULTIMO_ADMINISTRADOR");
        verify(users,never()).updateSecurity(any(),anyLong());verify(sessions,never()).revokeAll(anyLong(),any());
    }
    @Test void revocationFailureRollsBackThroughTransactionBoundary(){
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L,2L));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,1,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO)));
        when(users.findByIdForUpdate(2)).thenReturn(Optional.of(user(2,1,Rol.VENDEDOR,EstadoUsuario.ACTIVO)));
        when(users.updateSecurity(any(),eq(1L))).thenReturn(true);when(sessions.revokeAll(2,MotivoRevocacion.ROL_CAMBIADO)).thenThrow(new IllegalStateException("secret-canary"));
        assertThatThrownBy(() -> administration.role(principal,2,new RoleChange(Rol.ALMACENERO,1))).hasMessage("SERVICIO_NO_DISPONIBLE");
        verify(manager).rollback(any());verify(manager,never()).commit(any());
    }
    @Test void staleSecurityVersionRejectedBeforeMutation(){
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,1,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO)));
        when(users.findByIdForUpdate(2)).thenReturn(Optional.of(user(2,2,Rol.VENDEDOR,EstadoUsuario.ACTIVO)));
        assertThatThrownBy(() -> administration.state(principal,2,new StateChange(EstadoUsuario.INACTIVO,1))).hasMessage("VERSION_CONFLICTO");
        verify(users,never()).updateSecurity(any(),anyLong());
    }
    @Test void staleProfileTimestampRejectedBeforeMutation(){
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,1,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO)));
        when(users.findByIdForUpdate(2)).thenReturn(Optional.of(user(2,1,Rol.VENDEDOR,EstadoUsuario.ACTIVO)));
        assertThatThrownBy(() -> administration.profile(principal,2,new ProfileChange("Nuevo nombre",1,now.minusSeconds(1)))).hasMessage("VERSION_CONFLICTO");
        verify(users,never()).updateProfile(anyLong(),anyString(),anyLong(),any(),anyLong());
    }
    @Test void sessionValidationRejectsVersionMismatch(){
        String token=new OpaqueTokens().issue().value();var row=new Sesion(10L,1,new OpaqueTokens().hash(token),1,now,now.plusSeconds(60),null,null);
        when(sessions.findActiveByHash(any())).thenReturn(Optional.of(row));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,2,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO)));
        assertThatThrownBy(() -> auth.authenticate(token)).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
    }
    @Test void limiterCountsUnknownAccountsAndHasBoundedParallelWork(){
        var limiter=new LoginLimiter(Clock.fixed(now,ZoneOffset.UTC));
        for(int i=0;i<5;i++)limiter.attempt("unknown","source");
        assertThatThrownBy(() -> limiter.attempt("unknown","another-source")).hasMessage("LIMITE_LOGIN");
        for(int i=0;i<4;i++)limiter.acquire();assertThatThrownBy(limiter::acquire).hasMessage("LIMITE_LOGIN");
        for(int i=0;i<4;i++)limiter.release();
    }
}
