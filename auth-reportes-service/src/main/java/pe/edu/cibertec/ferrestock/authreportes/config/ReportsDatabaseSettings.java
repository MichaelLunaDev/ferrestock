package pe.edu.cibertec.ferrestock.authreportes.config;

import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/** Preparación explícita, sin @Configuration/@Bean: ninguna conexión ni lectura de entorno automática.
 * ReportConfig conserva UnconfiguredCommercialReader hasta aprobación del contrato físico. */
public final class ReportsDatabaseSettings {
    private static final String URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=ferrestock_ventas;encrypt=true;trustServerCertificate=false";
    private static final String USER="ferrestock_report_reader";
    private final String password;
    private ReportsDatabaseSettings(String password){this.password=password;}
    public static ReportsDatabaseSettings from(Environment env){
        try {
            if(env.matchesProfiles("auth-unit","auth-integration") || !env.matchesProfiles("auth-db")
                || !URL.equals(env.getProperty("REPORTS_DB_URL")) || !USER.equals(env.getProperty("REPORTS_DB_USER")))
                throw new IllegalStateException();
            String password=env.getProperty("REPORTS_DB_PASSWORD");
            if(password==null || password.isBlank())throw new IllegalStateException();
            return new ReportsDatabaseSettings(password);
        }catch(RuntimeException ex){throw new IllegalStateException("Configuracion SQL Reportes no autorizada o incompleta");}
    }
    /** Crear el objeto no abre conexiones. No se invoca desde el contexto actual. */
    public DataSource prepareDataSource(){
        var source=new DriverManagerDataSource();
        source.setUrl(URL+";loginTimeout=10");source.setUsername(USER);source.setPassword(password);
        return source;
    }
    @Override public String toString(){return "ReportsDatabaseSettings[REDACTED]";}
}
