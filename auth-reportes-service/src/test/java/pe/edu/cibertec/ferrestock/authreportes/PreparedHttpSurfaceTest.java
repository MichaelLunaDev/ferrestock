package pe.edu.cibertec.ferrestock.authreportes;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties={"PORT=0","spring.config.import="})
class PreparedHttpSurfaceTest {
    @Value("${local.server.port}") int port;
    @ParameterizedTest @ValueSource(strings={"/api/auth/login","/api/auth/logout","/api/auth/me","/api/sesiones"})
    void preparationDoesNotExposeAuthentication(String path) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(5)).GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
    }
}
