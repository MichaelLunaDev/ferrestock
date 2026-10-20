package pe.edu.cibertec.ferrestock.authreportes.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;

@RestController @RequestMapping("/api/auth") @Profile({"auth-db","auth-integration"})
public class AuthController {
    private final AutenticacionService auth;
    public AuthController(AutenticacionService auth){this.auth=auth;}
    @PostMapping("/login") public ResponseEntity<LoginResult> login(@Valid @RequestBody Login input,HttpServletRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(input,request.getRemoteAddr()));
    }
    @GetMapping("/me") public UserView me(@AuthenticationPrincipal AuthPrincipal principal){return auth.me(principal);}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthPrincipal principal){auth.logout(principal);}
}
