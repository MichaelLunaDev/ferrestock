package pe.edu.cibertec.ferrestock.authreportes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

// F1 no ofrece autenticacion: impedir la creacion del usuario automatico de Boot.
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class, DataSourceAutoConfiguration.class})
public class FerrestockAuthReportesApplication {

    public static void main(String[] args) {
        SpringApplication.run(FerrestockAuthReportesApplication.class, args);
    }
}
