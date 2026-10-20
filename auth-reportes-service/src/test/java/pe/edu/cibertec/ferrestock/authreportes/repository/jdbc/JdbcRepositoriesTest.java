package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class JdbcRepositoriesTest {
    private final NamedParameterJdbcOperations jdbc=mock(NamedParameterJdbcOperations.class);
    private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    private final DataSource source=mock(DataSource.class);
    private void activateAuthTransaction() {
        var template=mock(JdbcTemplate.class);
        when(jdbc.getJdbcOperations()).thenReturn(template);
        when(template.getDataSource()).thenReturn(source);
        var connection=mock(java.sql.Connection.class);
        try { when(connection.getAutoCommit()).thenReturn(false); }
        catch (java.sql.SQLException ex) { throw new AssertionError(ex); }
        var holder=new org.springframework.jdbc.datasource.ConnectionHolder(connection);
        holder.setSynchronizedWithTransaction(true);
        TransactionSynchronizationManager.bindResource(source,holder);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
    private Usuario user(Long id,long version) {
        return new Usuario(id,"canonical","Synthetic","HASH_CANARY",EstadoUsuario.ACTIVO,Rol.VENDEDOR,
                version,now,now,null,null);
    }
    @AfterEach void clearTransaction() {
        TransactionSynchronizationManager.unbindResourceIfPossible(source);
        TransactionSynchronizationManager.clear();
    }

    @Test void insertsPasswordOnlyAsBoundHashAndReturnsIdentity(CapturedOutput output) {
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(7L);
        assertThat(new JdbcUsuarioRepository(jdbc).insert(user(null,1))).isEqualTo(7);
        verify(jdbc).queryForObject(argThat(sql -> sql.contains(":hash") && sql.contains("OUTPUT INSERTED.usuario_id")
                && !sql.contains("HASH_CANARY")),argThat((SqlParameterSource p) -> p.getValue("hash").equals("HASH_CANARY")
                && p.getValue("rol").equals(2)),eq(Long.class));
        assertThat(output).doesNotContain("HASH_CANARY");
    }

    @Test void canonicalLoginIsBoundNotConcatenated() {
        when(jdbc.query(anyString(),anyMap(),org.mockito.ArgumentMatchers.<RowMapper<Usuario>>any())).thenReturn(List.of());
        assertThat(new JdbcUsuarioRepository(jdbc).findByLogin("canonical")).isEmpty();
        verify(jdbc).query(argThat(sql -> sql.contains("login=:login") && !sql.contains("canonical")),
                eq(Map.of("login","canonical")),org.mockito.ArgumentMatchers.<RowMapper<Usuario>>any());
        assertThatIllegalArgumentException().isThrownBy(() -> new JdbcUsuarioRepository(jdbc).findByLogin("CANONICAL"));
    }

    @Test void lockingAndMutationRequireTransaction() {
        var users=new JdbcUsuarioRepository(jdbc); var sessions=new JdbcSesionRepository(jdbc);
        assertThatIllegalStateException().isThrownBy(() -> users.findByIdForUpdate(1));
        assertThatIllegalStateException().isThrownBy(() -> users.updateSecurity(user(1L,2),1));
        assertThatIllegalStateException().isThrownBy(() -> sessions.revokeAll(1,MotivoRevocacion.ROL_CAMBIADO));
        verifyNoInteractions(jdbc);
    }

    @Test void securityUpdateUsesCompareAndSwap() {
        activateAuthTransaction(); // Doble; no transaccion SQL real.
        when(jdbc.update(anyString(),any(SqlParameterSource.class))).thenReturn(0,1);
        var users=new JdbcUsuarioRepository(jdbc);
        assertThat(users.updateSecurity(user(1L,2),1)).isFalse();
        assertThat(users.updateSecurity(user(1L,2),1)).isTrue();
        verify(jdbc,times(2)).update(argThat(sql -> sql.contains("version_seguridad=:expected")
                && sql.contains("version_seguridad=:version")),argThat((SqlParameterSource p) ->
                p.getValue("expected").equals(1L) && p.getValue("version").equals(2L)));
        assertThatIllegalArgumentException().isThrownBy(() -> users.updateSecurity(user(1L,3),1));
    }

    @Test void sessionInsertContainsOnlyHashAndUtcDateTime() {
        activateAuthTransaction();
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class))).thenReturn(9L);
        byte[] hash=new byte[32]; hash[0]=12;
        assertThat(new JdbcSesionRepository(jdbc).insert(new Sesion(null,1,hash,1,now,now.plusSeconds(60),null,null)))
                .isEqualTo(9);
        verify(jdbc).queryForObject(argThat(sql -> sql.contains("token_hash") && !sql.contains("raw_token")),
                argThat((SqlParameterSource p) -> Arrays.equals((byte[])p.getValue("hash"),hash)
                        && p.getValue("creado").equals(LocalDateTime.of(2026,1,1,0,0))),eq(Long.class));
    }

    @Test void activeSessionLookupHasAllSecurityPredicatesAndHashParameter() {
        when(jdbc.query(anyString(),anyMap(),org.mockito.ArgumentMatchers.<RowMapper<Sesion>>any())).thenReturn(List.of());
        assertThat(new JdbcSesionRepository(jdbc).findActiveByHash(new byte[32])).isEmpty();
        verify(jdbc).query(argThat(sql -> sql.contains("s.token_hash=:hash") && sql.contains("s.revocado_en IS NULL")
                && sql.contains("s.vence_en>SYSUTCDATETIME()") && sql.contains("u.estado='ACTIVO'")
                && sql.contains("s.version_seguridad=u.version_seguridad")),anyMap(),
                org.mockito.ArgumentMatchers.<RowMapper<Sesion>>any());
    }

    @Test void revocationsAreIdempotentAndScopedToUser() {
        activateAuthTransaction();
        var sessions=new JdbcSesionRepository(jdbc);
        sessions.revoke(9,1,MotivoRevocacion.LOGOUT);
        sessions.revokeAll(1,MotivoRevocacion.CREDENCIAL_CAMBIADA);
        verify(jdbc).update(argThat(sql -> sql.contains("sesion_id=:id AND usuario_id=:usuario AND revocado_en IS NULL")),
                eq(Map.of("id",9L,"usuario",1L,"motivo","LOGOUT")));
        verify(jdbc).update(argThat(sql -> sql.contains("WHERE usuario_id=:usuario AND revocado_en IS NULL")),
                eq(Map.of("usuario",1L,"motivo","CREDENCIAL_CAMBIADA")));
    }

    @Test void repositoryErrorsAreSanitized(CapturedOutput output) {
        when(jdbc.queryForObject(anyString(),any(SqlParameterSource.class),eq(Long.class)))
                .thenThrow(new DataAccessResourceFailureException("SECRET_SQL_CANARY password=secret"));
        assertThat(catchThrowable(() -> new JdbcUsuarioRepository(jdbc).insert(user(null,1))))
                .hasMessage("Operacion de persistencia Auth rechazada").hasNoCause();
        assertThat(output).doesNotContain("SECRET_SQL_CANARY","password=secret");
    }

    @Test void rowMapperReadsUtcAndSingleRoleWithoutSql() throws Exception {
        when(jdbc.query(anyString(),anyMap(),org.mockito.ArgumentMatchers.<RowMapper<Usuario>>any())).thenAnswer(invocation -> {
            RowMapper<Usuario> mapper=invocation.getArgument(2);
            ResultSet rs=mock(ResultSet.class);
            when(rs.getLong("usuario_id")).thenReturn(1L); when(rs.getLong("version_seguridad")).thenReturn(1L);
            when(rs.getInt("rol_id")).thenReturn(2); when(rs.getString("login")).thenReturn("canonical");
            when(rs.getString("nombre")).thenReturn("Synthetic"); when(rs.getString("password_hash")).thenReturn("HASH_CANARY");
            when(rs.getString("estado")).thenReturn("ACTIVO");
            when(rs.getObject(eq("creado_en"),eq(LocalDateTime.class))).thenReturn(LocalDateTime.of(2026,1,1,0,0));
            when(rs.getObject(eq("actualizado_en"),eq(LocalDateTime.class))).thenReturn(LocalDateTime.of(2026,1,1,0,0));
            return List.of(mapper.mapRow(rs,0));
        });
        var user=new JdbcUsuarioRepository(jdbc).findByLogin("canonical").orElseThrow();
        assertThat(user.rol()).isEqualTo(Rol.VENDEDOR);
        assertThat(user.creadoEn()).isEqualTo(now);
    }
    @Test void newAdministrativeMutationsAlsoRequireAuthTransaction(){
        var users=new JdbcUsuarioRepository(jdbc);
        assertThatIllegalStateException().isThrownBy(users::lockActiveAdministrators);
        assertThatIllegalStateException().isThrownBy(users::lockEmptyUserTable);
        assertThatIllegalStateException().isThrownBy(() -> users.updateProfile(1,"Name",1,now,1));verifyNoInteractions(jdbc);
    }
    @Test void administratorBarrierUsesRangeLockAndExpectedIndex(){
        activateAuthTransaction();when(jdbc.queryForList(anyString(),anyMap(),eq(Long.class))).thenReturn(List.of(1L));
        assertThat(new JdbcUsuarioRepository(jdbc).lockActiveAdministrators()).containsExactly(1L);
        verify(jdbc).queryForList(argThat(sql -> sql.contains("UPDLOCK,HOLDLOCK") && sql.contains("INDEX(IX_auth_usuario_rol_estado)") && sql.contains("rol_id=1 AND estado='ACTIVO'")),anyMap(),eq(Long.class));
    }
    @Test void bootstrapChecksEmptyTableUnderLock(){
        activateAuthTransaction();when(jdbc.queryForObject(anyString(),anyMap(),eq(Long.class))).thenReturn(0L,1L);
        var users=new JdbcUsuarioRepository(jdbc);assertThat(users.lockEmptyUserTable()).isTrue();assertThat(users.lockEmptyUserTable()).isFalse();
        verify(jdbc,times(2)).queryForObject(argThat(sql -> sql.contains("COUNT_BIG(*)") && sql.contains("UPDLOCK,HOLDLOCK")),anyMap(),eq(Long.class));
    }
    @Test void profileUpdateBindsTimestampAndVersionCas(){
        activateAuthTransaction();when(jdbc.update(anyString(),anyMap())).thenReturn(1,0);
        var users=new JdbcUsuarioRepository(jdbc);assertThat(users.updateProfile(1,"Name",2,now,3)).isTrue();assertThat(users.updateProfile(1,"Name",2,now,3)).isFalse();
        verify(jdbc,times(2)).update(argThat(sql -> sql.contains("version_seguridad=:version AND actualizado_en=:updated") && sql.contains("DATEADD(millisecond,1,actualizado_en)")),
            argThat((Map<String,?> p) -> p.get("updated").equals(LocalDateTime.of(2026,1,1,0,0)) && p.get("version").equals(2L) && p.get("actor").equals(3L)));
    }
    @Test void namedAuthManagerIsExplicit()throws Exception{
        var method=pe.edu.cibertec.ferrestock.authreportes.config.AuthServiceConfig.class.getDeclaredMethod("authTransactions",org.springframework.transaction.PlatformTransactionManager.class);
        assertThat(method.getParameters()[0].getAnnotation(org.springframework.beans.factory.annotation.Qualifier.class).value()).isEqualTo("authTransactionManager");
    }
}
