package pe.edu.cibertec.ferrestock.authreportes;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class LocalRuntimeTest {
    @Test void stopMarkerClosesOnlyItsInputLoop() {
        assertThatCode(() -> LocalRuntime.awaitStop(new StringReader("ignored\nSTOP_FERRESTOCK_LOCAL\n"))).doesNotThrowAnyException();
    }
    @Test void supervisorDisconnectAlsoClosesItsInputLoop() {
        assertThatCode(() -> LocalRuntime.awaitStop(new StringReader(""))).doesNotThrowAnyException();
    }
    @Test void argumentsRejectedBeforeSpringOrSql() {
        assertThatThrownBy(() -> LocalRuntime.main(new String[]{"unexpected"})).isInstanceOf(IllegalArgumentException.class);
    }
}
