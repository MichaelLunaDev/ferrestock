package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Supplier;

final class JdbcSupport {
    private JdbcSupport() { }
    static <T> T safely(Supplier<T> work) {
        try { return work.get(); }
        catch (DataAccessException ex) { throw new IllegalStateException("Operacion de persistencia Auth rechazada"); }
    }
    static void requireTransaction(NamedParameterJdbcOperations jdbc) {
        // Defensa local, no sustituye @Transactional con authTransactionManager.
        // Toda comprobacion queda dentro del catch para no divulgar fallos de adaptadores/driver.
        try {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !(jdbc.getJdbcOperations() instanceof JdbcTemplate template)
                    || template.getDataSource() == null
                    || !TransactionSynchronizationManager.hasResource(template.getDataSource())) {
                throw new IllegalStateException("Se requiere transaccion Auth");
            }
            Object resource = TransactionSynchronizationManager.getResource(template.getDataSource());
            if (!(resource instanceof ConnectionHolder holder) || !holder.isSynchronizedWithTransaction()) {
                throw new IllegalStateException("Se requiere transaccion Auth");
            }
            if (holder.getConnection().getAutoCommit()) {
                throw new IllegalStateException("Se requiere transaccion Auth");
            }
        } catch (SQLException | RuntimeException ex) {
            throw new IllegalStateException("Se requiere transaccion Auth");
        }
    }
    static Instant utc(ResultSet row, String name) throws SQLException {
        LocalDateTime date = row.getObject(name, LocalDateTime.class);
        return date == null ? null : date.toInstant(ZoneOffset.UTC);
    }
    static LocalDateTime utc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
