package pe.edu.cibertec.ferrestock.authreportes.config;

import java.util.Set;
import static pe.edu.cibertec.ferrestock.authreportes.config.MigrationPolicy.*;

/** No automatic retries: an exception after any commit requires a fresh administrative inventory. */
final class MigrationFlow {
    interface Engine {
        Set<String> objects() throws Exception;
        void inspect(boolean complete) throws Exception;
        void validate() throws Exception;
        void migrate() throws Exception;
    }
    static void run(Action action, Engine engine) throws Exception {
        MigrationPolicy.objects(engine.objects(), action == Action.migrate);
        engine.inspect(false);
        if (action == Action.info) return;
        if (action == Action.migrate) engine.migrate();
        if (!engine.objects().equals(OBJECTS)) throw blocked("ESTADO_INCOMPLETO");
        engine.inspect(true);
        engine.validate();
    }
    private MigrationFlow() { }
}
