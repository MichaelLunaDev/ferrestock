package pe.edu.cibertec.ferrestock.authreportes.entity;

import java.time.Instant;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Modelo interno; no DTO HTTP. Valores sin id se usan exclusivamente para altas. */
public record Usuario(Long id, String login, String nombre, @JsonIgnore String passwordHash, EstadoUsuario estado,
                      Rol rol, long versionSeguridad, Instant creadoEn, Instant actualizadoEn,
                      Long creadoPor, Long actualizadoPor) {
    public Usuario {
        Objects.requireNonNull(rol);
        Objects.requireNonNull(estado);
        Objects.requireNonNull(passwordHash);
        if (versionSeguridad < 1) throw new IllegalArgumentException("Version de seguridad invalida");
    }
    @Override public String toString() { return "Usuario[id=" + id + ", secretos=REDACTED]"; }
}
