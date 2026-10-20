package pe.edu.cibertec.ferrestock.authreportes.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import java.util.function.Supplier;

/** Manager Auth inyectado por nombre. Incluye COMMIT dentro del limite saneado. */
public final class AuthTransactions {
    private final TransactionTemplate ordinary;
    private final TransactionTemplate administration;
    public AuthTransactions(PlatformTransactionManager manager) {
        ordinary=new TransactionTemplate(manager);
        ordinary.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ordinary.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ordinary.setTimeout(15);
        administration=new TransactionTemplate(manager);
        administration.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        administration.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        administration.setTimeout(15);
    }
    public <T> T run(Supplier<T> work) { return execute(ordinary,work); }
    public <T> T admin(Supplier<T> work) { return execute(administration,work); }
    private <T> T execute(TransactionTemplate template, Supplier<T> work) {
        try { return template.execute(status -> work.get()); }
        catch (ApiProblem ex) { throw ex; }
        catch (RuntimeException ex) { throw ApiProblem.unavailable(); }
    }
}
