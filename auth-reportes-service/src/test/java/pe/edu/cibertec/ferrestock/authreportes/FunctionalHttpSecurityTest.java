package pe.edu.cibertec.ferrestock.authreportes;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.cibertec.ferrestock.authreportes.controller.AuthController;
import pe.edu.cibertec.ferrestock.authreportes.controller.UsuarioController;
import pe.edu.cibertec.ferrestock.authreportes.controller.ReportController;
import pe.edu.cibertec.ferrestock.authreportes.dto.AuthDtos.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.EstadoUsuario;
import pe.edu.cibertec.ferrestock.authreportes.service.UsuarioService;
import pe.edu.cibertec.ferrestock.authreportes.reportes.*;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.*;
import pe.edu.cibertec.ferrestock.authreportes.reportes.ReportModels.Period;
import java.time.*;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.RequestBuilder;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import pe.edu.cibertec.ferrestock.authreportes.entity.Rol;
import pe.edu.cibertec.ferrestock.authreportes.exception.ApiProblem;
import pe.edu.cibertec.ferrestock.authreportes.security.AuthPrincipal;
import pe.edu.cibertec.ferrestock.authreportes.service.AutenticacionService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;

/** Solo test, servicios dobles sin perfiles SQL ni rutas de simulacion productivas. */
@SpringBootTest(properties="spring.config.import=")
@Import(FunctionalHttpSecurityTest.Doubles.class)
@ExtendWith(OutputCaptureExtension.class)
class FunctionalHttpSecurityTest {
    @TestConfiguration(proxyBeanMethods=false) static class Doubles {
        @Bean AutenticacionService authDouble(){return mock(AutenticacionService.class);}
        @Bean AuthController authTestController(AutenticacionService service){return new AuthController(service);}
        @Bean UsuarioService userDouble(){return mock(UsuarioService.class);}
        @Bean UsuarioController userTestController(UsuarioService service){return new UsuarioController(service);}
        @Bean ReportService reportDouble(){return mock(ReportService.class);}
        @Bean DocumentExporter exporter(){return new DocumentExporter();}
        @Bean ReportController reportTestController(ReportService service,DocumentExporter exporter){return new ReportController(service,exporter);}
    }
    @Autowired WebApplicationContext context;@Autowired AutenticacionService auth;
    @Autowired UsuarioService users;@Autowired ReportService reports;
    MockMvc mvc;
    @BeforeEach void setup(){reset(auth,users,reports);mvc=webAppContextSetup(context).apply(springSecurity()).build();}
    final String token="a".repeat(43);
    AuthPrincipal admin(){var principal=new AuthPrincipal(1,2,1,Rol.ADMINISTRADOR);when(auth.authenticate(token)).thenReturn(principal);return principal;}
    UserView view(){return new UserView(1,"user","Name",EstadoUsuario.ACTIVO,Rol.ADMINISTRADOR,1,Instant.EPOCH,Instant.EPOCH);}
    ResultActions checked(RequestBuilder request)throws Exception{return mvc.perform(request).andExpect(header().doesNotExist("Set-Cookie")).andExpect(r -> assertThat(r.getRequest().getSession(false)).isNull());}
    @Test void missingBearerHas401()throws Exception{mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));}
    @Test void sellerHas403ForAdministration()throws Exception{
        when(auth.authenticate("a".repeat(43))).thenReturn(new AuthPrincipal(1,2,1,Rol.VENDEDOR));
        mvc.perform(get("/api/usuarios").header("Authorization","Bearer "+"a".repeat(43))).andExpect(status().isForbidden());
    }
    @Test void malformedBearerRejectedWithoutCallingSqlService()throws Exception{
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());verifyNoInteractions(auth);
    }
    @Test void invalidSessionFailsClosedWithoutSecret()throws Exception{
        when(auth.authenticate("a".repeat(43))).thenThrow(ApiProblem.unauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+"a".repeat(43))).andExpect(status().isUnauthorized()).andExpect(content().json("{\"code\":\"CREDENCIALES_O_SESION_INVALIDAS\"}"));
    }
    @Test void duplicateAuthorizationRejected()throws Exception{
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+"a".repeat(43),"Bearer "+"b".repeat(43))).andExpect(status().isUnauthorized());verifyNoInteractions(auth);
    }
    @Test void loginValidatesDtoWithoutReflectingPassword()throws Exception{
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"login\":\"admin\",\"password\":\"short\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().json("{\"code\":\"SOLICITUD_INVALIDA\"}"));verifyNoInteractions(auth);
    }
    @Test void healthDoesNotAuthenticateOrCreateSession()throws Exception{checked(get("/api/health").header("Authorization","Bearer broken")).andExpect(status().isOk());verifyNoInteractions(auth);}
    @Test void publicLoginReturnsTokenWithNoStore()throws Exception{
        when(auth.login(any(),anyString())).thenReturn(new LoginResult("SYNTHETIC_TOKEN","Bearer",Instant.EPOCH,view()));
        checked(post("/api/auth/login").contentType("application/json").content("{\"login\":\"user\",\"password\":\"fixture-password\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store"))).andExpect(jsonPath("$.accessToken").value("SYNTHETIC_TOKEN"));
        verify(auth,never()).authenticate(any());
    }
    @Test void incorrectLoginReturnsGeneric401()throws Exception{
        when(auth.login(any(),anyString())).thenThrow(ApiProblem.unauthorized());
        checked(post("/api/auth/login").contentType("application/json").content("{\"login\":\"user\",\"password\":\"fixture-password\"}"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer")).andExpect(content().json("{\"code\":\"CREDENCIALES_O_SESION_INVALIDAS\"}"));
    }
    @Test void logoutWithoutBearerIs401()throws Exception{checked(post("/api/auth/logout")).andExpect(status().isUnauthorized());}
    @Test void authenticatedMeReturnsOnlyProfile()throws Exception{var p=admin();when(auth.me(p)).thenReturn(view());checked(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));}
    @Test void logoutThenReuseIs401()throws Exception{
        var p=admin();var active=new java.util.concurrent.atomic.AtomicBoolean(true);
        when(auth.authenticate(token)).thenAnswer(call -> {if(!active.get())throw ApiProblem.unauthorized();return p;});
        doAnswer(call -> {active.set(false);return null;}).when(auth).logout(p);
        checked(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        checked(post("/api/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void warehouseRoleCannotReadReports()throws Exception{when(auth.authenticate(token)).thenReturn(new AuthPrincipal(1,2,1,Rol.ALMACENERO));checked(get("/api/reportes/stock-bajo").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());verifyNoInteractions(reports);}
    @Test void administratorCanListUsers()throws Exception{var p=admin();when(users.list(p,0,25)).thenReturn(List.of(view()));checked(get("/api/usuarios").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));}
    @ParameterizedTest @ValueSource(ints={400,401,403,404,409,422,429,503})
    void publicErrorsUseStatusCodeAndNoStore(int status)throws Exception{
        var p=admin();when(users.get(p,2)).thenThrow(new ApiProblem(status,"FIXTURE_CODE"));
        var result=checked(get("/api/usuarios/2").header("Authorization","Bearer "+token)).andExpect(status().is(status)).andExpect(header().string("Cache-Control",containsString("no-store"))).andExpect(content().json("{\"code\":\"FIXTURE_CODE\"}"));
        if(status==429)result.andExpect(header().string("Retry-After","60"));if(status==401)result.andExpect(header().string("WWW-Authenticate","Bearer"));
    }
    @Test void unexpectedDriverFailureNeverLeaksStackOrCause(CapturedOutput output)throws Exception{var p=admin();when(users.get(p,2)).thenThrow(new IllegalStateException("SQL_PASSWORD_CANARY"));checked(get("/api/usuarios/2").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable()).andExpect(content().json("{\"code\":\"SERVICIO_NO_DISPONIBLE\"}"));assertThat(output).doesNotContain("SQL_PASSWORD_CANARY");}
    @Test void login429SetsRetryAfter()throws Exception{when(auth.login(any(),anyString())).thenThrow(new ApiProblem(429,"LIMITE_LOGIN"));checked(post("/api/auth/login").contentType("application/json").content("{\"login\":\"user\",\"password\":\"fixture-password\"}")).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));}
    @Test void remoteAddressIsNotForwardedFor()throws Exception{
        checked(post("/api/auth/login").with(r -> {r.setRemoteAddr("192.0.2.10");return r;}).header("X-Forwarded-For","203.0.113.1").contentType("application/json").content("{\"login\":\"user\",\"password\":\"fixture-password\"}")).andExpect(status().isOk());
        verify(auth).login(any(),eq("192.0.2.10"));
    }
    @Test void noBasicOrFormLoginOrUnknownRoutes()throws Exception{checked(get("/api/auth/me").header("Authorization","Basic abc")).andExpect(status().isUnauthorized());checked(post("/login")).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));admin();checked(get("/does-not-exist").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());}
    @Test void corsDoesNotAllowArbitraryOrigins()throws Exception{admin();checked(get("/api/usuarios").header("Authorization","Bearer "+token).header("Origin","https://untrusted.invalid")).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));}
    @Test void missingDatesAndInvalidFormatsAre400()throws Exception{admin();checked(get("/api/reportes/ventas").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());checked(get("/api/reportes/stock-bajo?formato=CSV").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());}
    @Test void unconfiguredReportsReturn503InsteadOfDownload()throws Exception{admin();when(reports.stock(any(),anyInt())).thenThrow(new ApiProblem(503,"CONTRATO_COMERCIAL_PENDIENTE"));checked(get("/api/reportes/stock-bajo?formato=PDF").header("Authorization","Bearer "+token)).andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Content-Disposition")).andExpect(content().json("{\"code\":\"CONTRATO_COMERCIAL_PENDIENTE\"}"));}
    @ParameterizedTest @ValueSource(strings={"JSON","PDF","XLSX"})
    void formatsUseSameReportAndCorrectDownloadHeaders(String format)throws Exception{
        var p=admin();var period=new Period(LocalDate.of(2026,1,1),LocalDate.of(2026,1,2));var report=new SalesReport(period,List.of(new Sale(1,period.start(),"Seller",new BigDecimal("12.50"),"COMPLETADA")));
        when(reports.sales(p,period)).thenReturn(report);when(reports.table(report)).thenReturn(new Table("Ventas","2026-01-01 a 2026-01-02",List.of("Total"),List.of(List.of(new BigDecimal("12.50")))));
        var result=checked(get("/api/reportes/ventas?desde=2026-01-01&hasta=2026-01-02&formato="+format).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",containsString("no-store")));
        if(format.equals("JSON")){result.andExpect(content().contentTypeCompatibleWith("application/json")).andExpect(jsonPath("$.filas[0].total").value(12.5));}
        else{var response=result.andReturn().getResponse();assertThat(response.getContentLength()).isEqualTo(response.getContentAsByteArray().length);assertThat(response.getHeader("Content-Disposition")).contains("attachment","ventas."+format.toLowerCase());assertThat(response.getContentType()).isEqualTo(format.equals("PDF")?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");}
        verify(reports,times(1)).sales(p,period);
    }
    @Test void wrongLoginMediaTypeMustBe415()throws Exception{checked(post("/api/auth/login").contentType("text/plain").content("fixture")).andExpect(status().isUnsupportedMediaType());}
    @Test void staleBearerCannotPreventPublicLogin()throws Exception{when(auth.login(any(),anyString())).thenReturn(new LoginResult("SYNTHETIC","Bearer",Instant.EPOCH,view()));checked(post("/api/auth/login").header("Authorization","Bearer stale").contentType("application/json").content("{\"login\":\"user\",\"password\":\"fixture-password\"}")).andExpect(status().isOk());verify(auth,never()).authenticate(any());}
    @Test void unsupportedResponseFormatIs406Not503()throws Exception{var p=admin();when(auth.me(p)).thenReturn(view());checked(get("/api/auth/me").header("Authorization","Bearer "+token).accept("application/xml")).andExpect(status().isNotAcceptable());}
    @Test void rejectedPasswordNeverAppearsInFrameworkLogs(CapturedOutput output)throws Exception{checked(post("/api/auth/login").contentType("application/json").content("{\"login\":\"user\",\"password\":\"S3cr3tXYZ\"}")).andExpect(status().isBadRequest());assertThat(output).doesNotContain("S3cr3tXYZ");}
    @Test void publicHealthWithContextPathRemainsPublic()throws Exception{checked(get("/ferrestock/api/health").contextPath("/ferrestock").header("Authorization","Bearer invalid")).andExpect(status().isOk());verifyNoInteractions(auth);}
}
