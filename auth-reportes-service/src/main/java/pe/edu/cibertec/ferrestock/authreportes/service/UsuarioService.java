package pe.edu.cibertec.ferrestock.authreportes.service;

import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.*;

public final class UsuarioService {
    private final UsuarioRepository users;private final RolRepository roles;private final SesionRepository sessions;
    private final PasswordEncoder passwords;private final AuthTransactions tx;private final AutenticacionService auth;
    public UsuarioService(UsuarioRepository users,RolRepository roles,SesionRepository sessions,
                          PasswordEncoder passwords,AuthTransactions tx,AutenticacionService auth) {
        this.users=users;this.roles=roles;this.sessions=sessions;this.passwords=passwords;this.tx=tx;this.auth=auth;
    }
    public UserView create(AuthPrincipal actor,CreateUser input) {
        String login;
        try{login=LoginNormalizer.normalize(input.login());}catch(IllegalArgumentException ex){throw new ApiProblem(400,"LOGIN_INVALIDO");}
        String nombre=validateName(input.nombre());
        if(input.rol()==null) throw new ApiProblem(400,"ROL_REQUERIDO");
        String hash=encode(input.password());
        return tx.admin(() -> {
            users.lockActiveAdministrators();auth.requireCurrent(actor,true);
            if(!roles.findAll().containsAll(List.of(Rol.values())) || roles.findAll().size()!=3) throw ApiProblem.unavailable();
            if(users.findByLogin(login).isPresent()) throw new ApiProblem(409,"LOGIN_EN_USO");
            long id=users.insert(new Usuario(null,login,nombre,hash,EstadoUsuario.ACTIVO,input.rol(),1,null,null,actor.usuarioId(),actor.usuarioId()));
            return UserView.of(users.findById(id).orElseThrow(ApiProblem::unavailable));
        });
    }
    public List<UserView> list(AuthPrincipal actor,int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw new ApiProblem(400,"PAGINA_INVALIDA");
        return tx.admin(() -> {users.lockActiveAdministrators();auth.requireCurrent(actor,true);
            return users.findPage(Math.multiplyExact(page,size),size).stream().map(UserView::of).toList();});
    }
    public UserView get(AuthPrincipal actor,long id) {
        return tx.admin(() -> {users.lockActiveAdministrators();auth.requireCurrent(actor,true);
            return UserView.of(users.findById(id).orElseThrow(() -> new ApiProblem(404,"USUARIO_NO_ENCONTRADO")));});
    }
    public UserView profile(AuthPrincipal actor,long id,ProfileChange input) {
        String name=validateName(input.nombre());
        if(input.actualizadoEnEsperado()==null) throw new ApiProblem(400,"FECHA_ESPERADA_REQUERIDA");
        return tx.admin(() -> {
            users.lockActiveAdministrators();auth.requireCurrent(actor,true);
            Usuario current=lock(id,input.versionEsperada());
            if(!input.actualizadoEnEsperado().equals(current.actualizadoEn())) throw conflict();
            if(!users.updateProfile(id,name,current.versionSeguridad(),input.actualizadoEnEsperado(),actor.usuarioId())) throw conflict();
            return UserView.of(users.findById(id).orElseThrow(ApiProblem::unavailable));
        });
    }
    public UserView password(AuthPrincipal actor,long id,PasswordChange input) {
        String hash=encode(input.password());
        return change(actor,id,input.versionEsperada(),hash,null,null,MotivoRevocacion.CREDENCIAL_CAMBIADA);
    }
    public UserView role(AuthPrincipal actor,long id,RoleChange input) {
        if(input.rol()==null) throw new ApiProblem(400,"ROL_REQUERIDO");
        return change(actor,id,input.versionEsperada(),null,input.rol(),null,MotivoRevocacion.ROL_CAMBIADO);
    }
    public UserView state(AuthPrincipal actor,long id,StateChange input) {
        if(input.estado()==null) throw new ApiProblem(400,"ESTADO_REQUERIDO");
        return change(actor,id,input.versionEsperada(),null,null,input.estado(),MotivoRevocacion.USUARIO_INACTIVO);
    }
    private UserView change(AuthPrincipal actor,long id,long expected,String hash,Rol role,EstadoUsuario state,MotivoRevocacion reason) {
        return tx.admin(() -> {
            List<Long> admins=users.lockActiveAdministrators();auth.requireCurrent(actor,true);
            Usuario current=lock(id,expected);
            Rol nextRole=role==null?current.rol():role;EstadoUsuario nextState=state==null?current.estado():state;
            if(current.rol()==Rol.ADMINISTRADOR && current.estado()==EstadoUsuario.ACTIVO && admins.size()<=1
                    && (nextRole!=Rol.ADMINISTRADOR || nextState!=EstadoUsuario.ACTIVO)) throw new ApiProblem(409,"ULTIMO_ADMINISTRADOR");
            if(current.versionSeguridad()==Long.MAX_VALUE) throw conflict();
            Usuario next=new Usuario(current.id(),current.login(),current.nombre(),hash==null?current.passwordHash():hash,
                    nextState,nextRole,current.versionSeguridad()+1,current.creadoEn(),current.actualizadoEn(),current.creadoPor(),actor.usuarioId());
            if(!users.updateSecurity(next,expected)) throw conflict();
            sessions.revokeAll(id,reason);
            return UserView.of(users.findById(id).orElseThrow(ApiProblem::unavailable));
        });
    }
    private Usuario lock(long id,long expected) {
        if(id<1 || expected<1) throw new ApiProblem(400,"VERSION_O_ID_INVALIDO");
        Usuario current=users.findByIdForUpdate(id).orElseThrow(() -> new ApiProblem(404,"USUARIO_NO_ENCONTRADO"));
        if(current.versionSeguridad()!=expected) throw conflict();
        return current;
    }
    private String encode(String raw) {
        if(raw==null || raw.length()<12 || raw.length()>128) throw new ApiProblem(400,"CREDENCIAL_INVALIDA");
        return passwords.encode(raw);
    }
    private static String validateName(String name) {
        if(name==null || name.isBlank() || name.length()>120 || name.chars().anyMatch(Character::isISOControl)) throw new ApiProblem(400,"NOMBRE_INVALIDO");
        return name.strip();
    }
    private static ApiProblem conflict(){return new ApiProblem(409,"VERSION_CONFLICTO");}
}
