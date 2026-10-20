package pe.edu.cibertec.ferrestock.authreportes.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UsuarioLifecycleTest {
    UsuarioRepository users=mock(UsuarioRepository.class);SesionRepository sessions=mock(SesionRepository.class);
    RolRepository roles=mock(RolRepository.class);PasswordEncoder encoder=mock(PasswordEncoder.class);
    PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    Instant now=Instant.parse("2026-01-01T00:00:00Z");UsuarioService service;
    AuthPrincipal actor=new AuthPrincipal(1,10,1,Rol.ADMINISTRADOR);
    Usuario user(long id,Rol rol,EstadoUsuario estado,long version){return new Usuario(id,"user"+id,"Name","HASH",estado,rol,version,now,now,null,null);}
    @BeforeEach void setup(){
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(encoder.encode(any())).thenReturn("NEXT_HASH");
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L));
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO,1)));
        when(users.findByIdForUpdate(2)).thenReturn(Optional.of(user(2,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1)));
        when(roles.findAll()).thenReturn(List.of(Rol.values()));
        var tx=new AuthTransactions(manager);var auth=new AutenticacionService(users,sessions,encoder,new OpaqueTokens(),tx,new LoginLimiter(Clock.fixed(now,ZoneOffset.UTC)));
        service=new UsuarioService(users,roles,sessions,encoder,tx,auth);
    }
    @Test void createNormalizesAndDerivesAuditActor(){
        when(users.insert(any())).thenReturn(2L);when(users.findById(2)).thenReturn(Optional.of(user(2,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1)));
        service.create(actor,new CreateUser("  MIXED.Name "," Persona ","fixture-password",Rol.VENDEDOR));
        var created=org.mockito.ArgumentCaptor.forClass(Usuario.class);verify(users).insert(created.capture());
        assertThat(created.getValue().login()).isEqualTo("mixed.name");assertThat(created.getValue().nombre()).isEqualTo("Persona");
        assertThat(created.getValue().passwordHash()).isEqualTo("NEXT_HASH");assertThat(created.getValue().creadoPor()).isEqualTo(1);
        var order=inOrder(users);order.verify(users).lockActiveAdministrators();order.verify(users).findByIdForUpdate(1);order.verify(users).findByLogin("mixed.name");order.verify(users).insert(any());
    }
    @Test void duplicateLoginRejected(){
        when(users.findByLogin("existing")).thenReturn(Optional.of(user(2,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1)));
        assertThatThrownBy(() -> service.create(actor,new CreateUser("EXISTING","Name","fixture-password",Rol.VENDEDOR))).hasMessage("LOGIN_EN_USO");
        verify(users,never()).insert(any());
    }
    @Test void mandatoryRoleRejected(){assertThatThrownBy(() -> service.create(actor,new CreateUser("valid","Name","fixture-password",null))).hasMessage("ROL_REQUERIDO");verifyNoInteractions(manager);}
    @ParameterizedTest @ValueSource(strings={"", " ", "bad\nname"})
    void invalidProfileNameRejected(String name){assertThatThrownBy(() -> service.profile(actor,2,new ProfileChange(name,1,now))).hasMessage("NOMBRE_INVALIDO");verifyNoInteractions(manager);}
    @Test void profileUpdateUsesBothCasValuesAndDoesNotRevoke(){
        when(users.updateProfile(2,"New",1,now,1)).thenReturn(true);when(users.findById(2)).thenReturn(Optional.of(user(2,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1)));
        service.profile(actor,2,new ProfileChange("New",1,now));verify(users).updateProfile(2,"New",1,now,1);verifyNoInteractions(sessions);verify(manager).commit(any());
    }
    @Test void profileCasFailureRollsBack(){
        assertThatThrownBy(() -> service.profile(actor,2,new ProfileChange("New",1,now))).hasMessage("VERSION_CONFLICTO");verify(manager).rollback(any());
    }
    @ParameterizedTest @ValueSource(strings={"password","role","state"})
    void securityChangeAndFullRevocationShareOneTransaction(String operation){
        AtomicReference<Usuario> stored=new AtomicReference<>(user(2,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1));
        when(users.updateSecurity(any(),eq(1L))).thenAnswer(call -> {stored.set(call.getArgument(0));return true;});
        when(users.findById(2)).thenAnswer(call -> Optional.of(stored.get()));
        MotivoRevocacion reason;
        UserView result;
        switch(operation){
            case "password" -> {reason=MotivoRevocacion.CREDENCIAL_CAMBIADA;result=service.password(actor,2,new PasswordChange("fixture-password",1));}
            case "role" -> {reason=MotivoRevocacion.ROL_CAMBIADO;result=service.role(actor,2,new RoleChange(Rol.ALMACENERO,1));}
            default -> {reason=MotivoRevocacion.USUARIO_INACTIVO;result=service.state(actor,2,new StateChange(EstadoUsuario.INACTIVO,1));}
        }
        assertThat(result.versionSeguridad()).isEqualTo(2);assertThat(stored.get().actualizadoPor()).isEqualTo(1);
        var order=inOrder(users,sessions,manager);
        order.verify(manager).getTransaction(any());order.verify(users).lockActiveAdministrators();order.verify(users).findByIdForUpdate(1);
        order.verify(users).findByIdForUpdate(2);order.verify(users).updateSecurity(any(),eq(1L));order.verify(sessions).revokeAll(2,reason);
        order.verify(users).findById(2);order.verify(manager).commit(any());
    }
    @Test void lastAdministratorCannotBeDisabled(){
        assertThatThrownBy(() -> service.state(actor,1,new StateChange(EstadoUsuario.INACTIVO,1))).hasMessage("ULTIMO_ADMINISTRADOR");verify(sessions,never()).revokeAll(anyLong(),any());
    }
    @Test void secondAdministratorCanBeDisabled(){
        when(users.lockActiveAdministrators()).thenReturn(List.of(1L,2L));when(users.findByIdForUpdate(2)).thenReturn(Optional.of(user(2,Rol.ADMINISTRADOR,EstadoUsuario.ACTIVO,1)));
        when(users.updateSecurity(any(),eq(1L))).thenReturn(true);when(users.findById(2)).thenReturn(Optional.of(user(2,Rol.ADMINISTRADOR,EstadoUsuario.INACTIVO,2)));
        assertThat(service.state(actor,2,new StateChange(EstadoUsuario.INACTIVO,1)).estado()).isEqualTo(EstadoUsuario.INACTIVO);
    }
    @Test void concurrentRoleChangeDetectedByVersionCas(){
        // Modelo de una carrera perdida, no dos transacciones SQL ejecutadas.
        when(users.updateSecurity(any(),eq(1L))).thenReturn(false);
        assertThatThrownBy(() -> service.role(actor,2,new RoleChange(Rol.ALMACENERO,1))).hasMessage("VERSION_CONFLICTO");verifyNoInteractions(sessions);verify(manager).rollback(any());
    }
    @Test void catalogMustBeExactlyThreeRoles(){
        when(roles.findAll()).thenReturn(List.of(Rol.ADMINISTRADOR));
        assertThatThrownBy(() -> service.create(actor,new CreateUser("valid","Name","fixture-password",Rol.VENDEDOR))).hasMessage("SERVICIO_NO_DISPONIBLE");verify(users,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(ints={0,-1,101})
    void invalidPageSizeRejected(int size){assertThatThrownBy(() -> service.list(actor,0,size)).hasMessage("PAGINA_INVALIDA");}
    @Test void missingUserReturnsNotFound(){assertThatThrownBy(() -> service.get(actor,999)).hasMessage("USUARIO_NO_ENCONTRADO");}
    @Test void nonAdministratorRejectedInsideService(){
        when(users.findByIdForUpdate(1)).thenReturn(Optional.of(user(1,Rol.VENDEDOR,EstadoUsuario.ACTIVO,1)));
        assertThatThrownBy(() -> service.list(actor,0,25)).hasMessage("ACCESO_DENEGADO");
    }
}
