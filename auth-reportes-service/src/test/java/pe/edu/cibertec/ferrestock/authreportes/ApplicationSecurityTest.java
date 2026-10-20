package pe.edu.cibertec.ferrestock.authreportes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"PORT=0", "spring.config.import="})
class ApplicationSecurityTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    ApplicationContext context;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void startsOnConfiguredEphemeralPortWithoutDatabaseOrDefaultUsers() {
        assertThat(port).isPositive();
        assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("0");
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("auth-reportes-service");
    }

    @Test
    void publicHealthReturnsOnlyServiceStatusWithoutSession() throws Exception {
        var response = send("GET", "/api/health", false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.body()).isIn(
                "{\"status\":\"UP\",\"service\":\"auth-reportes-service\"}",
                "{\"service\":\"auth-reportes-service\",\"status\":\"UP\"}");
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"GET,/api/usuarios", "GET,/api/reportes", "GET,/does-not-exist", "GET,/error",
            "GET,/login", "POST,/login", "POST,/logout", "POST,/api/health", "HEAD,/api/health",
            "OPTIONS,/api/health", "GET,/api/health/child"})
    void otherRoutesAndMethodsAreDenied(String method, String path) throws Exception {
        var response = send(method, path, false);
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }

    @Test
    void basicCredentialsCannotOpenDeniedRoute() throws Exception {
        assertThat(send("GET", "/api/usuarios", true).statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> send(String method, String path, boolean basic) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).method(method, HttpRequest.BodyPublishers.noBody());
        if (basic) {
            request.header("Authorization", "Basic dXNlcjppbnZhbGlk");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
