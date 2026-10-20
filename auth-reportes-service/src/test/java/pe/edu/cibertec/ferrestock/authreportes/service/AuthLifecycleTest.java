package pe.edu.cibertec.ferrestock.authreportes.service;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Protocolo y fallos del cliente con dobles: no prueba aislamiento SQL. */
class AuthLifecycleTest {
    final UsuarioRepository users=mock(UsuarioRepository.class);
    final SesionRepository sessions=mock(SesionRepository.class);
    final PasswordEncoder passwords=mock(PasswordEncoder.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    final OpaqueTokens tokens=new OpaqueTokens();
    AuthTransactions tx;AutenticacionService auth;
    Usuario user(EstadoUsuario state,long version,String hash){return new Usuario(1L,"canonical","Name",hash,state,Rol.VENDEDOR,version,now,now,null,null);}
    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(passwords.encode(any())).thenReturn("DUMMY_HASH");
        tx=new AuthTransactions(manager);auth=new AutenticacionService(users,sessions,passwords,tokens,tx,new LoginLimiter(Clock.fixed(now,ZoneOffset.UTC)));
    }
    void valid(){
        var user=user(EstadoUsuario.ACTIVO,1,"HASH");
        when(users.findByLogin("canonical")).thenReturn(Optional.of(user));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user));
        when(passwords.matches("fixture-password","HASH")).thenReturn(true);when(users.databaseNow()).thenReturn(now);
    }
    @Test void incorrectPasswordDoesNotOpenTransactionOrInsert(){
        valid();when(passwords.matches(any(),eq("HASH"))).thenReturn(false);
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verifyNoInteractions(manager,sessions);
    }
    @Test void nonexistentUserStillPerformsDummyVerification(){
        when(users.findByLogin("canonical")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verify(passwords).matches("fixture-password","DUMMY_HASH");verifyNoInteractions(manager,sessions);
    }
    @Test void inactiveUserDoesNotSkipKdf(){
        when(users.findByLogin("canonical")).thenReturn(Optional.of(user(EstadoUsuario.INACTIVO,1,"HASH")));when(passwords.matches(any(),eq("HASH"))).thenReturn(true);
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verify(passwords).matches("fixture-password","HASH");verifyNoInteractions(manager,sessions);
    }
    @ParameterizedTest @ValueSource(strings={"hash","inactive","missing"})
    void changeWhileKdfWasRunningPreventsSession(String change){
        valid();
        when(users.findByIdForUpdate(1)).thenReturn(change.equals("missing")?Optional.empty():Optional.of(user(change.equals("inactive")?EstadoUsuario.INACTIVO:EstadoUsuario.ACTIVO,1,change.equals("hash")?"NEW_HASH":"HASH")));
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verify(sessions,never()).insert(any());verify(manager).rollback(any());
    }
    @Test void commitFailureDoesNotReturnTokenOrExposeCause(){
        valid();doThrow(new TransactionSystemException("TOKEN_SQL_PASSWORD_CANARY")).when(manager).commit(any());
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).isInstanceOf(ApiProblem.class).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();
        verify(sessions).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"INSERT_ERROR","UNIQUE_TOKEN_COLLISION"})
    void insertionFailureOrCollisionRollsBackWithoutRetry(String failure){
        valid();when(sessions.insert(any())).thenThrow(new IllegalStateException(failure+"_SECRET_CANARY"));
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("SERVICIO_NO_DISPONIBLE").hasNoCause();
        verify(sessions,times(1)).insert(any());verify(manager).rollback(any());verify(manager,never()).commit(any());
    }
    @ParameterizedTest @ValueSource(strings={"expired","revoked","missing"})
    void absentActiveSessionFromSqlContractIsRejected(String reason){
        // El doble representa la ausencia; el predicado de tiempo real requiere SQL.
        when(sessions.findActiveByHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> auth.authenticate(tokens.issue().value())).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        verify(users,never()).findByIdForUpdate(anyLong());
    }
    @Test void revocationBetweenInitialLookupAndUserLockIsRejected(){
        String token=tokens.issue().value();Sesion session=new Sesion(10L,1,tokens.hash(token),1,now,now.plusSeconds(60),null,null);
        when(sessions.findActiveByHash(any())).thenReturn(Optional.of(session),Optional.empty());
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(EstadoUsuario.ACTIVO,1,"HASH")));
        assertThatThrownBy(() -> auth.authenticate(token)).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        var order=inOrder(sessions,users);order.verify(sessions).findActiveByHash(any());order.verify(users).findByIdForUpdate(1);order.verify(sessions).findActiveByHash(any());
    }
    @Test void validSessionCreatesOnlyCurrentAuthority(){
        String token=tokens.issue().value();Sesion session=new Sesion(10L,1,tokens.hash(token),1,now,now.plusSeconds(60),null,null);
        when(sessions.findActiveByHash(any())).thenReturn(Optional.of(session));when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(EstadoUsuario.ACTIVO,1,"HASH")));
        assertThat(auth.authenticate(token)).isEqualTo(new AuthPrincipal(1,10,1,Rol.VENDEDOR));
    }
    @Test void logoutRepeatsAsIdempotentRepositoryUpdate(){
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(EstadoUsuario.ACTIVO,1,"HASH")));
        when(sessions.revoke(10,1,MotivoRevocacion.LOGOUT)).thenReturn(1,0);
        var principal=new AuthPrincipal(1,10,1,Rol.VENDEDOR);auth.logout(principal);auth.logout(principal);
        verify(sessions,times(2)).revoke(10,1,MotivoRevocacion.LOGOUT);verify(manager,times(2)).commit(any());
    }
    @Test void managerUsesNewReadCommittedOrSerializableTransactions(){
        tx.run(() -> 1);tx.admin(() -> 2);
        var definitions=org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);verify(manager,times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(d -> {assertThat(d.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);assertThat(d.getTimeout()).isEqualTo(15);});
        assertThat(definitions.getAllValues()).extracting(TransactionDefinition::getIsolationLevel).containsExactly(TransactionDefinition.ISOLATION_READ_COMMITTED,TransactionDefinition.ISOLATION_SERIALIZABLE);
    }
    @Test void inputAndOutputObjectsNeverPrintPasswordsOrToken(){
        valid();var input=new Login("canonical","fixture-password");var response=auth.login(input,"source");
        assertThat(input.toString()).doesNotContain(input.password());assertThat(response.toString()).doesNotContain(response.accessToken(),"HASH");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(input)).doesNotContain("fixture-password","password");
    }
    @Test void successesAndFailuresBothConsumeLoginBudget(){
        valid();auth.login(new Login("canonical","fixture-password"),"source");
        when(passwords.matches("fixture-password","HASH")).thenReturn(false);
        for(int i=0;i<4;i++)assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("CREDENCIALES_O_SESION_INVALIDAS");
        when(passwords.matches("fixture-password","HASH")).thenReturn(true);
        assertThatThrownBy(() -> auth.login(new Login("canonical","fixture-password"),"source")).hasMessage("LIMITE_LOGIN");verify(users,times(5)).findByLogin("canonical");
    }
}
