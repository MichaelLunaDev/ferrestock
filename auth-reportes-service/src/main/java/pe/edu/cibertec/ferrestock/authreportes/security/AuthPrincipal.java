package pe.edu.cibertec.ferrestock.authreportes.security;
import pe.edu.cibertec.ferrestock.authreportes.entity.Rol;
/** Identidad de una peticion, nunca contiene token ni hash. */
public record AuthPrincipal(long usuarioId, long sesionId, long versionSeguridad, Rol rol) { }
