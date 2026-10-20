package pe.edu.cibertec.ferrestock.authreportes.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.service.UsuarioService;

@RestController @RequestMapping("/api/usuarios") @Profile({"auth-db","auth-integration"})
public class UsuarioController {
    private final UsuarioService users;
    public UsuarioController(UsuarioService users){this.users=users;}
    @PostMapping public ResponseEntity<UserView> create(@AuthenticationPrincipal AuthPrincipal p,@Valid @RequestBody CreateUser dto){
        var created=users.create(p,dto);return ResponseEntity.created(URI.create("/api/usuarios/"+created.id())).body(created);
    }
    @GetMapping public List<UserView> list(@AuthenticationPrincipal AuthPrincipal p,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return users.list(p,page,size);}
    @GetMapping("/{id}") public UserView get(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id){return users.get(p,id);}
    @PutMapping("/{id}") public UserView profile(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@Valid @RequestBody ProfileChange dto){return users.profile(p,id,dto);}
    @PutMapping("/{id}/password") public UserView password(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@Valid @RequestBody PasswordChange dto){return users.password(p,id,dto);}
    @PutMapping("/{id}/rol") public UserView role(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@Valid @RequestBody RoleChange dto){return users.role(p,id,dto);}
    @PutMapping("/{id}/estado") public UserView state(@AuthenticationPrincipal AuthPrincipal p,@PathVariable long id,@Valid @RequestBody StateChange dto){return users.state(p,id,dto);}
}
