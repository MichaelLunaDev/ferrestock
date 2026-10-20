package pe.edu.cibertec.ferrestock.authreportes.entity;

import java.time.Instant;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** No contiene token original. Copias defensivas del hash en entrada y salida. */
public record Sesion(Long id, long usuarioId, @JsonIgnore byte[] tokenHash, long versionSeguridad,
                     Instant creadoEn, Instant venceEn, Instant revocadoEn, MotivoRevocacion motivo) {
    public Sesion {
        Objects.requireNonNull(tokenHash);
        if (tokenHash.length != 32 || versionSeguridad < 1) throw new IllegalArgumentException("Sesion invalida");
        tokenHash = tokenHash.clone();
        if (creadoEn == null || venceEn == null || !venceEn.isAfter(creadoEn)
                || (revocadoEn == null) != (motivo == null)
                || (revocadoEn != null && revocadoEn.isBefore(creadoEn))) {
            throw new IllegalArgumentException("Fechas de sesion invalidas");
        }
    }
    @Override @JsonIgnore public byte[] tokenHash() { return tokenHash.clone(); }
    @Override public String toString() { return "Sesion[id=" + id + ", secretos=REDACTED]"; }
}
