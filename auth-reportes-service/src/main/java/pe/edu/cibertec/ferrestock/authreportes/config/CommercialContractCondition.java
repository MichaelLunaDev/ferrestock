package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class CommercialContractCondition implements Condition {
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return ReviewedCommercialContracts.schema(context.getEnvironment()).isPresent();
    }
}
