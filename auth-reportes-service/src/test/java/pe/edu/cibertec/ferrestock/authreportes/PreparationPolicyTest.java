package pe.edu.cibertec.ferrestock.authreportes;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import static org.assertj.core.api.Assertions.*;

class PreparationPolicyTest {
    @Test void pendingManifestHasExactCountsAndNoDuplicateAcl() throws Exception {
        var json=tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(Files.readString(Path.of("docs/F2B-002B-MANIFEST.json")));
        assertThat(json.get("status").asString()).isEqualTo("PROPUESTO_NO_AUTORIZADO");
        assertThat(json.get("objects").size()).isEqualTo(8);
        assertThat(json.get("database_acl").size()).isEqualTo(59);
        assertThat(json.get("stage_acl_counts").get("current").asInt()).isEqualTo(43);
        assertThat(json.get("stage_acl_counts").get("test_owner_visibility").asInt()).isEqualTo(44);
        assertThat(json.get("stage_acl_counts").get("test_runtime").asInt()).isEqualTo(51);
        assertThat(json.get("stage_acl_counts").get("dev_owner_visibility").asInt()).isEqualTo(52);
        assertThat(json.get("stage_acl_counts").get("dev_runtime").asInt()).isEqualTo(59);
        var ownerDatabases=new java.util.HashSet<String>();
        var distinct=new java.util.HashSet<String>();
        json.get("database_acl").forEach(row -> {
            assertThat(row.get("minor_id").asInt()).isZero();
            assertThat(distinct.add(row.toString())).isTrue();
            if(row.get("class").asInt()==4) {
                assertThat(row.get("principal").asString()).isEqualTo("ferrestock_auth_migrator");
                assertThat(row.get("object").asString()).isEqualTo("auth_owner");
                assertThat(row.get("schema").asString()).isEmpty();
                assertThat(row.get("permission").asString()).isEqualTo("VIEW DEFINITION");
                assertThat(row.get("state").asString()).isEqualTo("G");
                assertThat(ownerDatabases.add(row.get("database").asString())).isTrue();
            }
        });
        assertThat(ownerDatabases).containsExactlyInAnyOrder("ferrestock_auth_test","ferrestock_auth_dev");
    }
    @Test void flywayIsExplicitSkippedAndHasNoLifecycleExecution() throws Exception {
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var pom=factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        var xpath=XPathFactory.newInstance().newXPath();
        String profile="/project/profiles/profile[id='db-migration']";
        String plugin=profile+"/build/plugins/plugin[artifactId='flyway-maven-plugin']";
        assertThat(xpath.evaluate(plugin+"/configuration/skip",pom)).isEqualTo("true");
        assertThat(xpath.evaluate("count("+plugin+"/executions/execution)",pom)).isEqualTo("0");
        assertThat(xpath.evaluate("count(/project/build/plugins/plugin[artifactId='flyway-maven-plugin'])",pom)).isEqualTo("0");
        assertThat(xpath.evaluate(plugin+"/configuration/defaultSchema",pom)).isEqualTo("auth");
        assertThat(xpath.evaluate(plugin+"/configuration/cleanDisabled",pom)).isEqualTo("true");
        assertThat(xpath.evaluate("count("+plugin+"/configuration/password)",pom)).isEqualTo("0");
        assertThat(xpath.evaluate("/project/build/plugins/plugin[artifactId='maven-surefire-plugin']"
                +"/configuration/systemPropertyVariables/spring.profiles.active",pom)).isEqualTo("auth-unit");
        assertThatThrownBy(() -> Class.forName("org.flywaydb.core.Flyway")).isInstanceOf(ClassNotFoundException.class);
    }

    @Test void migrationsContainOnlyApprovedTablesAndNoPermissionsOrUsers() throws Exception {
        String v1=Files.readString(Path.of("src/main/resources/db/migration/V1__auth_core.sql"));
        String v2=Files.readString(Path.of("src/main/resources/db/migration/V2__roles.sql"));
        assertThat(v1.lines().filter(line -> line.startsWith("CREATE TABLE ")))
                .containsExactly("CREATE TABLE auth.rol (","CREATE TABLE auth.usuario (","CREATE TABLE auth.sesion (");
        assertThat(v1).contains("FOREIGN KEY (rol_id) REFERENCES auth.rol(rol_id)","token_hash binary(32) NOT NULL",
                "UQ_auth_sesion_token", "UQ_auth_usuario_login", "WHERE revocado_en IS NULL", "authentication_type=0",
                "pbkdf2-ferrestock-v1", "version_seguridad >= 1");
        assertThat(v1+v2).doesNotContain("GRANT ","DENY ","CREATE USER ","CREATE LOGIN ","auth_test_app");
        assertThat(v2).contains("(1,'ADMINISTRADOR'),(2,'VENDEDOR'),(3,'ALMACENERO')")
                .doesNotContain("CLIENTE", "password_hash");
        assertThat(Files.readString(Path.of("src/main/resources/application.yaml"))).contains("mode: never");
    }
}
