package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import pe.edu.cibertec.ferrestock.authreportes.reportes.*;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;

@Configuration(proxyBeanMethods=false) @Profile({"auth-db","auth-integration"})
public class ReportConfig {
    @Bean("reportsDataSource") @Conditional(CommercialContractCondition.class)
    DataSource reportsDataSource(Environment environment){return ReportsDatabaseSettings.from(environment).prepareDataSource();}
    @Bean("reportsCommercialSql") @Conditional(CommercialContractCondition.class)
    CandidateCommercialSql reportsCommercialSql(Environment environment){
        return new CandidateCommercialSql(ReviewedCommercialContracts.schema(environment).orElseThrow());
    }
    @Bean CommercialReportReader commercialReportReader(
            @Qualifier("reportsDataSource") ObjectProvider<DataSource> source,
            @Qualifier("reportsCommercialSql") ObjectProvider<CandidateCommercialSql> sql){
        var datasource=source.getIfAvailable();var queries=sql.getIfAvailable();
        if(datasource==null || queries==null)return new UnconfiguredCommercialReader();
        return new JdbcCommercialReportReader(datasource,queries);
    }
    @Bean ReportService reportService(CommercialReportReader reader,AutenticacionService auth){return new ReportService(reader,auth);}
    @Bean DocumentExporter documentExporter(){return new DocumentExporter();}
}
