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
    private FirstAdministrator() { }
    public static void main(String[] args) {
        var console=System.console();
        if(args.length!=0 || console==null){System.err.println("Bootstrap requiere consola segura y ningun argumento.");System.exit(1);return;}
        char[] password=null,confirmation=null;
        boolean success=false;
        try {
            String login=LoginNormalizer.normalize(console.readLine("Login del primer administrador: "));
            String nombre=console.readLine("Nombre: ");
            if(nombre==null || nombre.isBlank() || nombre.length()>120 || nombre.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
            password=console.readPassword("Contrasena (12-128): ");confirmation=console.readPassword("Confirmar: ");
            if(password==null || confirmation==null || password.length<12 || password.length>128 || !Arrays.equals(password,confirmation)) throw new IllegalArgumentException();
            try(var context=new AnnotationConfigApplicationContext()){
                context.getEnvironment().setActiveProfiles("auth-db");
                context.register(AuthDataSourceConfig.class,AuthRepositoryConfig.class,CryptoConfig.class);context.refresh();
                var users=context.getBean(UsuarioRepository.class);var roles=context.getBean(RolRepository.class);
                String hash=context.getBean(PasswordEncoder.class).encode(CharBuffer.wrap(password));
                var tx=new AuthTransactions(context.getBean("authTransactionManager",PlatformTransactionManager.class));
                String cleanName=nombre.strip();
                tx.admin(() -> {
                    if(!users.lockEmptyUserTable()) throw new IllegalStateException();
                    var catalog=roles.findAll();
                    if(catalog.size()!=3 || !catalog.containsAll(List.of(Rol.values())))throw new IllegalStateException();
                    users.insert(new Usuario(null,login,cleanName,hash,EstadoUsuario.ACTIVO,Rol.ADMINISTRADOR,1,null,null,null,null));
                    return null;
                });
                console.printf("Administrador creado; no se muestran credenciales.%n");
                success=true;
            }
        }catch(RuntimeException ex){console.printf("Bootstrap rechazado. Revisar precondiciones sin exponer secretos.%n");}
        finally {if(password!=null)Arrays.fill(password,'\0');if(confirmation!=null)Arrays.fill(confirmation,'\0');}
        if(!success)System.exit(1);
    }
}
