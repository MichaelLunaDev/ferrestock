package pe.edu.cibertec.ferrestock.authreportes.repository.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TransactionGuardTest {
    private final DataSource auth = mock(DataSource.class);
    private final DataSource other = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(auth);

    @AfterEach void cleanup() {
        TransactionSynchronizationManager.unbindResourceIfPossible(auth);
        TransactionSynchronizationManager.unbindResourceIfPossible(other);
        TransactionSynchronizationManager.clear();
        verifyNoInteractions(auth, other); // Ninguna llamada getConnection, ni siquiera sobre mocks.
    }

    private ConnectionHolder holder(boolean synchronizedState) {
        var holder = new ConnectionHolder(connection);
        holder.setSynchronizedWithTransaction(synchronizedState);
        return holder;
    }
    private void active(Object resource) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(auth, resource);
    }
    private void rejected() {
        assertThatIllegalStateException().isThrownBy(() -> JdbcSupport.requireTransaction(jdbc))
                .withMessage("Se requiere transaccion Auth").withNoCause();
    }

    @Test void arbitraryObjectRejected() { active(new Object()); rejected(); }
    @Test void unsynchronizedHolderRejected() {
        active(holder(false)); rejected(); verifyNoInteractions(connection);
    }
    @Test void autoCommitTrueRejected() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        active(holder(true)); rejected();
    }
    @Test void noActiveSpringTransactionRejectedEvenWithBoundHolder() {
        TransactionSynchronizationManager.bindResource(auth, holder(true));
        rejected(); verifyNoInteractions(connection);
    }
    @Test void foreignDatasourceRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.bindResource(other, holder(true));
        rejected(); verifyNoInteractions(connection);
    }
    @Test void noBoundResourceRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(true); rejected();
    }
    @Test void validTransactionalDoubleAccepted() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        active(holder(true));
        assertThatCode(() -> JdbcSupport.requireTransaction(jdbc)).doesNotThrowAnyException();
        verify(connection).getAutoCommit();
    }
    @Test void jdbcFailureIsClosedAndSanitized(CapturedOutput output) throws Exception {
        when(connection.getAutoCommit()).thenThrow(new SQLException("A1_DRIVER_CANARY jdbc:secret;password=hidden"));
        active(holder(true)); rejected();
        assertThat(output).doesNotContain("A1_DRIVER_CANARY", "jdbc:secret", "password=hidden");
    }
    @Test void runtimeFailureDuringCheckIsClosed(CapturedOutput output) throws Exception {
        when(connection.getAutoCommit()).thenThrow(new IllegalStateException("A1_RUNTIME_CANARY"));
        active(holder(true)); rejected();
        assertThat(output).doesNotContain("A1_RUNTIME_CANARY");
    }
    @Test void adapterFailureIsAlsoSanitized() {
        var operations = mock(NamedParameterJdbcOperations.class);
        when(operations.getJdbcOperations()).thenThrow(new IllegalStateException("A1_ADAPTER_CANARY"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatIllegalStateException().isThrownBy(() -> JdbcSupport.requireTransaction(operations))
                .withMessage("Se requiere transaccion Auth").withNoCause();
    }
    @Test void holderWithoutConnectionFailsClosed() {
        // El constructor Connection rechaza null antes de invocar nuestra guardia.
        // Un handle sin conexion permite comprobar el fallo cerrado dentro de ella.
        var handle = mock(org.springframework.jdbc.datasource.ConnectionHandle.class);
        var empty = new ConnectionHolder(handle);
        empty.setSynchronizedWithTransaction(true); active(empty); rejected();
    }
}
