package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.core.env.Environment;
import javax.sql.DataSource;

/** Preparación explícita, sin @Configuration/@Bean: ninguna conexión ni lectura de entorno automática.
 * ReportConfig conserva UnconfiguredCommercialReader hasta aprobación del contrato físico. */
public final class ReportsDatabaseSettings {
    private static final String URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=false";
    private static final String USER="ferrestock_report_reader";
    private final String password;
    private final SqlTlsSettings tls;
    private ReportsDatabaseSettings(String password, SqlTlsSettings tls){this.password=password;this.tls=tls;}
    public static ReportsDatabaseSettings from(Environment env){
        try {
            if(env.matchesProfiles("auth-unit","auth-integration") || !env.matchesProfiles("auth-db")
                || !URL.equals(env.getProperty("REPORTS_DB_URL")) || !USER.equals(env.getProperty("REPORTS_DB_USER")))
                throw new IllegalStateException();
            String password=env.getProperty("REPORTS_DB_PASSWORD");
            if(password==null || password.isBlank())throw new IllegalStateException();
            return new ReportsDatabaseSettings(password, SqlTlsSettings.from(env));
        }catch(RuntimeException ex){throw new IllegalStateException("Configuracion SQL Reportes no autorizada o incompleta");}
    }
    /** Crear el objeto no abre conexiones. No se invoca desde el contexto actual. */
    public DataSource prepareDataSource(){
        return new StrictSqlDataSource(URL, USER, password, tls);
    }
    @Override public String toString(){return "ReportsDatabaseSettings[REDACTED]";}
}
