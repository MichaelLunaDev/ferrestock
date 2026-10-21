package pe.edu.cibertec.ferrestock.authreportes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;

/** Local supervisor entrypoint. Closing its private stdin closes the owned Spring context. */
public final class LocalRuntime {
    private LocalRuntime() { }
    static void awaitStop(Reader input) throws Exception {
        var reader = new BufferedReader(input);
        for (String line; (line = reader.readLine()) != null; ) {
            if ("STOP_FERRESTOCK_LOCAL".equals(line)) return;
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("Local runtime accepts no arguments");
        try (var context = SpringApplication.run(FerrestockAuthReportesApplication.class,
                "--spring.profiles.active=auth-db", "--server.address=127.0.0.1",
                "--server.port=8081", "--server.shutdown=graceful", "--debug=false")) {
            awaitStop(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
    }
}
