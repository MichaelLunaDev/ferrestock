package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.SesionRepository;
import java.util.Map;
import java.util.Optional;

public final class JdbcSesionRepository implements SesionRepository {
    private final NamedParameterJdbcOperations jdbc;
    public JdbcSesionRepository(NamedParameterJdbcOperations jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<Sesion> MAPPER = (rs, n) -> new Sesion(rs.getLong("sesion_id"),
            rs.getLong("usuario_id"), rs.getBytes("token_hash"), rs.getLong("version_seguridad"),
            JdbcSupport.utc(rs, "creado_en"), JdbcSupport.utc(rs, "vence_en"), JdbcSupport.utc(rs, "revocado_en"),
            rs.getString("motivo_revocacion") == null ? null : MotivoRevocacion.valueOf(rs.getString("motivo_revocacion")));

    @Override public long insert(Sesion s) {
        JdbcSupport.requireTransaction(jdbc);
        if (s.id() != null || s.revocadoEn() != null) throw new IllegalArgumentException("Alta de sesion invalida");
        return JdbcSupport.safely(() -> jdbc.queryForObject("""
                INSERT INTO auth.sesion (usuario_id,token_hash,version_seguridad,creado_en,vence_en)
                OUTPUT INSERTED.sesion_id VALUES (:usuario,:hash,:version,:creado,:vence)
                """, new MapSqlParameterSource().addValue("usuario", s.usuarioId()).addValue("hash", s.tokenHash())
                .addValue("version", s.versionSeguridad()).addValue("creado", JdbcSupport.utc(s.creadoEn()))
                .addValue("vence", JdbcSupport.utc(s.venceEn())), Long.class));
    }
    @Override public Optional<Sesion> findActiveByHash(byte[] hash) {
        if (hash == null || hash.length != 32) throw new IllegalArgumentException("Hash invalido");
        return JdbcSupport.safely(() -> jdbc.query("""
                SELECT s.sesion_id,s.usuario_id,s.token_hash,s.version_seguridad,s.creado_en,s.vence_en,
                       s.revocado_en,s.motivo_revocacion
                FROM auth.sesion s JOIN auth.usuario u ON u.usuario_id=s.usuario_id
                WHERE s.token_hash=:hash AND s.revocado_en IS NULL AND s.vence_en>SYSUTCDATETIME()
                  AND u.estado='ACTIVO' AND s.version_seguridad=u.version_seguridad
                """, Map.of("hash", hash.clone()), MAPPER).stream().findFirst());
    }
    @Override public int revoke(long sesionId, long usuarioId, MotivoRevocacion motivo) {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.update("""
                UPDATE auth.sesion SET revocado_en=SYSUTCDATETIME(),motivo_revocacion=:motivo
                WHERE sesion_id=:id AND usuario_id=:usuario AND revocado_en IS NULL
                """, Map.of("id", sesionId, "usuario", usuarioId, "motivo", motivo.name())));
    }
    @Override public int revokeAll(long usuarioId, MotivoRevocacion motivo) {
        JdbcSupport.requireTransaction(jdbc);
        return JdbcSupport.safely(() -> jdbc.update("""
                UPDATE auth.sesion SET revocado_en=SYSUTCDATETIME(),motivo_revocacion=:motivo
                WHERE usuario_id=:usuario AND revocado_en IS NULL
                """, Map.of("usuario", usuarioId, "motivo", motivo.name())));
    }
}
