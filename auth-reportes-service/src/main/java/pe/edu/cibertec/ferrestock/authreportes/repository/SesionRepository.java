package pe.edu.cibertec.ferrestock.authreportes.repository;

import pe.edu.cibertec.ferrestock.authreportes.entity.MotivoRevocacion;
import pe.edu.cibertec.ferrestock.authreportes.entity.Sesion;
import java.util.Optional;

public interface SesionRepository {
    long insert(Sesion sesion);
    /** SQL comprueba ACTIVO, vencimiento UTC, revocacion y version de usuario. */
    Optional<Sesion> findActiveByHash(byte[] hash);
    /** Requieren transaccion y bloqueo previo de usuario por el futuro servicio. */
    int revoke(long sesionId, long usuarioId, MotivoRevocacion motivo);
    int revokeAll(long usuarioId, MotivoRevocacion motivo);
}
