package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import pe.edu.cibertec.ferrestock.authreportes.entity.Rol;
import pe.edu.cibertec.ferrestock.authreportes.repository.RolRepository;
import java.util.List;
import java.util.Map;

public final class JdbcRolRepository implements RolRepository {
    private final NamedParameterJdbcOperations jdbc;
    public JdbcRolRepository(NamedParameterJdbcOperations jdbc) { this.jdbc = jdbc; }
    @Override public List<Rol> findAll() {
        return JdbcSupport.safely(() -> jdbc.query("SELECT rol_id, codigo FROM auth.rol ORDER BY rol_id", Map.of(), (rs, n) -> {
            Rol rol = Rol.fromId(rs.getInt("rol_id"));
            if (!rol.name().equals(rs.getString("codigo"))) throw new IllegalStateException("Catalogo Auth no conforme");
            return rol;
        }));
    }
}
