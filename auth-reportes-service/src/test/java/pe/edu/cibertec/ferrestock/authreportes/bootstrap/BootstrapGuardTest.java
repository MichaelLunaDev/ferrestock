package pe.edu.cibertec.ferrestock.authreportes.bootstrap;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

/** Proceso separado con pipes: aborta antes de crear contexto o conectar SQL. */
class BootstrapGuardTest {
    @ParameterizedTest @ValueSource(strings={"","unexpected"})
    void missingConsoleOrArgumentsRejectBeforeOpeningApplication(String argument)throws Exception{
        String javaBinary=System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")?"java.exe":"java";
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin",javaBinary).toString(),
            "-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),FirstAdministrator.class.getName()));
        if(!argument.isEmpty())command.add(argument);
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(15,TimeUnit.SECONDS)).isTrue();
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).isEqualTo(1);
            assertThat(output).contains("Bootstrap requiere consola segura").doesNotContain("Exception","DataSource","password=","jdbc:");
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
}
