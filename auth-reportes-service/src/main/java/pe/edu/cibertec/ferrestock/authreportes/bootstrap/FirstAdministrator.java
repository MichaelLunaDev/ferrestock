package pe.edu.cibertec.ferrestock.authreportes.bootstrap;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.cibertec.ferrestock.authreportes.config.*;
import pe.edu.cibertec.ferrestock.authreportes.entity.*;
import pe.edu.cibertec.ferrestock.authreportes.repository.*;
import pe.edu.cibertec.ferrestock.authreportes.security.LoginNormalizer;
import pe.edu.cibertec.ferrestock.authreportes.service.AuthTransactions;

/** Comando separado y sin servidor HTTP. Nunca se ejecuta al iniciar la aplicacion. */
public final class FirstAdministrator {
    enum Phase { LOGIN, NOMBRE, PASSWORD, CONFIGURACION, HASH, TRANSACCION, TABLA_VACIA, CATALOGO, INSERT, COMMIT }
    static String failure(Phase phase) {
        return "{\"Result\":\"BLOCKED\",\"Phase\":\""+phase.name()+"\",\"Category\":\"BOOTSTRAP_REJECTED\"}";
    }
    private FirstAdministrator() { }
    public static void main(String[] args) {
        var console=System.console();
        if(args.length!=0 || console==null){System.err.println("Bootstrap requiere consola segura y ningun argumento.");System.exit(1);return;}
        char[] password=null,confirmation=null;
        boolean success=false;
        Phase[] phase={Phase.LOGIN};
        try {
            String login=LoginNormalizer.normalize(console.readLine("Login del primer administrador: "));
            phase[0]=Phase.NOMBRE;
            String nombre=console.readLine("Nombre: ");
            if(nombre==null || nombre.isBlank() || nombre.length()>120 || nombre.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            phase[0]=Phase.PASSWORD;
            password=console.readPassword("Contrasena (12-128): ");confirmation=console.readPassword("Confirmar: ");
            if(password==null || confirmation==null || password.length<12 || password.length>128 || !Arrays.equals(password,confirmation)) throw new IllegalArgumentException();
            phase[0]=Phase.CONFIGURACION;
            try(var context=new AnnotationConfigApplicationContext()){
                context.getEnvironment().setActiveProfiles("auth-db");
                context.register(AuthDataSourceConfig.class,AuthRepositoryConfig.class,CryptoConfig.class);context.refresh();
                var users=context.getBean(UsuarioRepository.class);var roles=context.getBean(RolRepository.class);
                phase[0]=Phase.HASH;
                String hash=context.getBean(PasswordEncoder.class).encode(CharBuffer.wrap(password));
                var tx=new AuthTransactions(context.getBean("authTransactionManager",PlatformTransactionManager.class));
                String cleanName=nombre.strip();
                phase[0]=Phase.TRANSACCION;
                tx.admin(() -> {
                    phase[0]=Phase.TABLA_VACIA;
                    if(!users.lockEmptyUserTable()) throw new IllegalStateException();
                    phase[0]=Phase.CATALOGO;
                    var catalog=roles.findAll();
                    if(catalog.size()!=3 || !catalog.containsAll(List.of(Rol.values())))throw new IllegalStateException();
                    phase[0]=Phase.INSERT;
                    users.insert(new Usuario(null,login,cleanName,hash,EstadoUsuario.ACTIVO,Rol.ADMINISTRADOR,1,null,null,null,null));
                    phase[0]=Phase.COMMIT;
                    return null;
                });
                console.printf("Administrador creado; no se muestran credenciales.%n");
                success=true;
            }
        }catch(RuntimeException ex){System.out.println(failure(phase[0]));}
        finally {if(password!=null)Arrays.fill(password,'\0');if(confirmation!=null)Arrays.fill(confirmation,'\0');}
        if(!success)System.exit(1);
    }
}
