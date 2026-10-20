package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.UsuarioRepository;
import pe.edu.cibertec.ferrestock.authreportes.security.LoginNormalizer;
import java.util.Map;
import java.util.Optional;

public final class JdbcUsuarioRepository implements UsuarioRepository {
    private static final String COLUMNS = "usuario_id, login, nombre, password_hash, estado, rol_id, version_seguridad, "
            + "creado_en, actualizado_en, creado_por, actualizado_por";
    private final NamedParameterJdbcOperations jdbc;
    public JdbcUsuarioRepository(NamedParameterJdbcOperations jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<Usuario> MAPPER = (rs, n) -> new Usuario(
            rs.getLong("usuario_id"), rs.getString("login"), rs.getString("nombre"), rs.getString("password_hash"),
            EstadoUsuario.valueOf(rs.getString("estado")), Rol.fromId(rs.getInt("rol_id")), rs.getLong("version_seguridad"),
            JdbcSupport.utc(rs, "creado_en"), JdbcSupport.utc(rs, "actualizado_en"),
            rs.getObject("creado_por", Long.class), rs.getObject("actualizado_por", Long.class));

    @Override public Optional<Usuario> findById(long id) {
        return JdbcSupport.safely(() -> jdbc.query("SELECT " + COLUMNS + " FROM auth.usuario WHERE usuario_id=:id",
                Map.of("id", id), MAPPER).stream().findFirst());
    }
    @Override public java.util.List<Usuario> findPage(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new IllegalArgumentException("Pagina invalida");
        return JdbcSupport.safely(() -> jdbc.query("SELECT " + COLUMNS + " FROM auth.usuario ORDER BY usuario_id "
                + "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY", Map.of("offset", offset, "limit", limit), MAPPER));
    }
    @Override public java.util.List<Long> lockActiveAdministrators() {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.queryForList("""
                SELECT usuario_id FROM auth.usuario WITH (UPDLOCK,HOLDLOCK,INDEX(IX_auth_usuario_rol_estado))
                WHERE rol_id=1 AND estado='ACTIVO' ORDER BY usuario_id
                """, Map.of(), Long.class));
    }
    @Override public boolean updateProfile(long id, String nombre, long version, java.time.Instant expectedUpdatedAt, long actor) {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.update("""
                UPDATE auth.usuario SET nombre=:nombre,actualizado_por=:actor,
                actualizado_en=CASE WHEN CAST(SYSUTCDATETIME() AS datetime2(3))>actualizado_en
                    THEN CAST(SYSUTCDATETIME() AS datetime2(3)) ELSE DATEADD(millisecond,1,actualizado_en) END
                WHERE usuario_id=:id AND version_seguridad=:version AND actualizado_en=:updated
                """, Map.of("nombre", nombre, "actor", actor, "id", id, "version", version,
                        "updated", JdbcSupport.utc(expectedUpdatedAt)))) == 1;
    }
    @Override public java.time.Instant databaseNow() {
        return JdbcSupport.safely(() -> jdbc.queryForObject("SELECT SYSUTCDATETIME() AS ahora", Map.of(),
                (rs,n) -> JdbcSupport.utc(rs,"ahora")));
    }
    @Override public boolean lockEmptyUserTable() {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.queryForObject(
                "SELECT COUNT_BIG(*) FROM auth.usuario WITH (UPDLOCK,HOLDLOCK)", Map.of(), Long.class)) == 0;
    }

    @Override public Optional<Usuario> findByLogin(String login) {
        if (!LoginNormalizer.normalize(login).equals(login)) throw new IllegalArgumentException("Login no canonico");
        return JdbcSupport.safely(() -> jdbc.query("SELECT " + COLUMNS + " FROM auth.usuario WHERE login=:login",
                Map.of("login", login), MAPPER).stream().findFirst());
    }
    @Override public Optional<Usuario> findByIdForUpdate(long id) {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.query("SELECT " + COLUMNS
                + " FROM auth.usuario WITH (UPDLOCK, HOLDLOCK) WHERE usuario_id=:id", Map.of("id", id), MAPPER)
                .stream().findFirst());
    }
    @Override public long insert(Usuario u) {
        if (u.id() != null || !LoginNormalizer.normalize(u.login()).equals(u.login())) {
            throw new IllegalArgumentException("Alta de usuario invalida");
        }
        return JdbcSupport.safely(() -> jdbc.queryForObject("""
                INSERT INTO auth.usuario (login,nombre,password_hash,estado,rol_id,version_seguridad,creado_por,actualizado_por)
                OUTPUT INSERTED.usuario_id
                VALUES (:login,:nombre,:hash,:estado,:rol,:version,:actor,:actor)
                """, parameters(u).addValue("actor", u.creadoPor()), Long.class));
    }
    @Override public boolean updateSecurity(Usuario u, long expectedVersion) {
        JdbcSupport.requireTransaction(jdbc);
        if (u.id() == null || expectedVersion < 1 || expectedVersion == Long.MAX_VALUE
                || u.versionSeguridad() != expectedVersion + 1) throw new IllegalArgumentException("Version invalida");
        return JdbcSupport.safely(() -> jdbc.update("""
                UPDATE auth.usuario SET password_hash=:hash,estado=:estado,rol_id=:rol,
                version_seguridad=:version,actualizado_en=SYSUTCDATETIME(),actualizado_por=:actor
                WHERE usuario_id=:id AND version_seguridad=:expected
                """, parameters(u).addValue("id", u.id()).addValue("expected", expectedVersion)
                .addValue("actor", u.actualizadoPor()))) == 1;
    }
    private static MapSqlParameterSource parameters(Usuario u) {
        return new MapSqlParameterSource().addValue("login", u.login()).addValue("nombre", u.nombre())
                .addValue("hash", u.passwordHash()).addValue("estado", u.estado().name())
                .addValue("rol", u.rol().id()).addValue("version", u.versionSeguridad());
    }
}
