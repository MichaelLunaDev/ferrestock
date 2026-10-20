package pe.edu.cibertec.ferrestock.authreportes.repository;

import pe.edu.cibertec.ferrestock.authreportes.entity.Rol;
import java.util.List;

public interface RolRepository {
    /** Solo lectura del catalogo persistido; verificar coherencia id/codigo. */
    List<Rol> findAll();
}
