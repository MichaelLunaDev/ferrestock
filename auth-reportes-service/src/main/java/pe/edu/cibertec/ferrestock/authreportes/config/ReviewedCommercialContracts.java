package pe.edu.cibertec.ferrestock.authreportes.config;

import java.util.Map;
import java.util.Optional;
import org.springframework.core.env.Environment;

/** Registro de contratos revisados en código. Vacío hasta verificar físico, ACL y políticas.
 * Una variable de entorno nunca constituye aprobación ni acepta un schema arbitrario. */
final class ReviewedCommercialContracts {
    private static final Map<String,String> SCHEMAS = Map.of();
    private ReviewedCommercialContracts() { }
    static Optional<String> schema(Environment environment) {
        if (!environment.matchesProfiles("auth-db") || environment.matchesProfiles("auth-unit","auth-integration"))
            return Optional.empty();
        try {
            if (!"verified".equals(environment.getProperty("REPORTS_READER_MODE"))) return Optional.empty();
            String contract = environment.getProperty("REPORTS_CONTRACT_ID");
            return contract == null ? Optional.empty() : Optional.ofNullable(SCHEMAS.get(contract));
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }
}
