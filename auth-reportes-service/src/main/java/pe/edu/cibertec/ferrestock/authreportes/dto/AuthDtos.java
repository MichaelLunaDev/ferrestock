package pe.edu.cibertec.ferrestock.authreportes.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;

public final class AuthDtos {
    private AuthDtos() { }
    public record Login(@NotBlank @Size(max=64) String login,
                        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotNull @Size(min=12,max=128) String password) {
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    public record CreateUser(@NotBlank @Size(max=64) String login, @NotBlank @Size(max=120) String nombre,
                             @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotNull @Size(min=12,max=128) String password, @NotNull Rol rol) {
        @Override public String toString() { return "CreateUser[REDACTED]"; }
    }
    public record ProfileChange(@NotBlank @Size(max=120) String nombre, @Min(1) long versionEsperada,
                                @NotNull Instant actualizadoEnEsperado) { }
    public record PasswordChange(@JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @NotNull @Size(min=12,max=128) String password, @Min(1) long versionEsperada) {
        @Override public String toString() { return "PasswordChange[REDACTED]"; }
    }
    public record RoleChange(@NotNull Rol rol, @Min(1) long versionEsperada) { }
    public record StateChange(@NotNull EstadoUsuario estado, @Min(1) long versionEsperada) { }
    public record UserView(long id, String login, String nombre, EstadoUsuario estado, Rol rol,
                           long versionSeguridad, Instant creadoEn, Instant actualizadoEn) {
        public static UserView of(Usuario u) {
            return new UserView(u.id(),u.login(),u.nombre(),u.estado(),u.rol(),u.versionSeguridad(),u.creadoEn(),u.actualizadoEn());
        }
    }
    public record LoginResult(String accessToken, String tokenType, Instant expiresAt, UserView usuario) {
        @Override public String toString() { return "LoginResult[REDACTED]"; }
    }
}
