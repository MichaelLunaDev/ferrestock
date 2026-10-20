package pe.edu.cibertec.ferrestock.authreportes.repository;

import pe.edu.cibertec.ferrestock.authreportes.entity.Usuario;
import java.util.Optional;

public interface UsuarioRepository {
    Optional<Usuario> findById(long id);
    java.util.List<Usuario> findPage(int offset, int limit);
    /** Barrera de administracion: transaccion SERIALIZABLE, antes de bloquear usuarios. */
    java.util.List<Long> lockActiveAdministrators();
    boolean updateProfile(long id, String nombre, long expectedVersion, java.time.Instant expectedUpdatedAt, long actor);
    java.time.Instant databaseNow();
    /** Solo comando de bootstrap no HTTP, transaccion SERIALIZABLE. */
    boolean lockEmptyUserTable();
    Optional<Usuario> findByLogin(String canonicalLogin);
    /** Requiere transaccion authTransactionManager activa; orden usuario antes de sesion. */
    Optional<Usuario> findByIdForUpdate(long id);
    long insert(Usuario usuario);
    /** CAS: retorna false si version esperada no coincide. No revoca sesiones por si solo.
     * El futuro servicio debe coordinar update+revocacion en una sola transaccion. */
    boolean updateSecurity(Usuario usuario, long expectedVersion);
}
