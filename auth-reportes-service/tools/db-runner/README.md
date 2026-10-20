# Runner de migraciones Auth

CLI independiente de la aplicación. No incorpora Flyway al runtime de Spring Boot
ni cambia el perfil Maven `db-migration` bloqueado del [POM del servicio](../../pom.xml).
Reutiliza las clases TLS del módulo y las dos migraciones revisadas mediante SHA256
fijados en código. Cada ejecución atiende exactamente una base en loopback.

## Compilar y verificar sin SQL

Desde `auth-reportes-service`:

```powershell
.\mvnw.cmd -f tools/db-runner/pom.xml verify
```

Las pruebas usan dobles JDBC y Flyway; no demuestran migración SQL real.
El jar autónomo queda en `tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar`.
No contiene el backend ni sus endpoints. Los directorios target están excluidos de Git.

## Condiciones previas de ejecución real

Antes de abrir conexiones: certificado servidor instalado y aprobado, IP SAN correcta,
CA confiable, JKS **externo al repositorio** con certificados públicos, y variables
`SQL_TLS_TRUSTSTORE_PATH` / `SQL_TLS_TRUSTSTORE_PASSWORD` disponibles solo en el proceso.
No usar argumentos JVM para secretos ni archivos `.env` versionados.
La contraseña del migrator se pide mediante `System.console().readPassword()`;
una consola no disponible provoca bloqueo. No se lee una contraseña SQL del entorno.

Una auditoría administrativa contemporánea debe confirmar el estado completo de
la instancia, roles, rutas indirectas y ACL. Este runner comprueba en cada apertura
la identidad, SID, propietario, roles directos, privilegios administrativos relevantes
y las nueve ACL directas del migrator; **no sustituye los 54 controles administrativos**
ni prueba por sí solo todos los permisos heredados de `public` o entre bases.
No ejecutar `migrate` sin aprobación específica y sin esa auditoría conforme.

En una consola local segura, destinos y acciones de lectura son cerrados:

```powershell
java -jar tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar AuthTest info
java -jar tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar AuthTest validate
```

`validate` exige ambas migraciones aplicadas y objetos completos; una base todavía
vacía se inspecciona con `info`. Historial parcial, desconocido o fallido se bloquea.
Un historial completo también pasa por `Flyway.validate` para comprobar checksums.
`SPRING_PROFILES_ACTIVE`, si existe, debe ser exactamente `auth-integration` para
AuthTest o `auth-db` para AuthDev. Una identidad SQL configurada distinta del migrator
se rechaza; no hay fallback a cuentas administrativas o runtime.

## Migración inicial: requiere autorización separada

El runner es de **primera instalación**, no de actualización automática. Para migrate,
el esquema `auth` debe existir pero no contener objetos. Historial o tablas parcialmente
creados bloquean otra ejecución; primero se requiere inventario y revisión humana.

Después de aprobación de migrator AuthTest y auditoría conforme, estado 1/0:

```powershell
java -jar tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar AuthTest migrate 1/0
```

Después de migración, grants y validación funcional **real** de AuthTest aprobados,
auditoría conforme y autorización expresa de AuthDev, estado 3/1:

```powershell
java -jar tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar AuthDev migrate 3/1
```

El estado indicado y la confirmación de consola documentan la decisión del operador;
no son una firma ni evidencia independiente de que esa aprobación ocurrió.
No ejecutar estos ejemplos como parte de un build o del arranque HTTP.

Se crea una copia efímera de los bytes SQL verificados en target. Solo se admiten V1/V2;
archivos adicionales, callbacks SQL/Java y migraciones Java están excluidos. No existe
comando clean, baseline, repair, drop ni opción de URL/usuario arbitrarios. Los errores
se devuelven como JSON sanitizado, con exit code 1. No se publican causas del driver.

Flyway puede confirmar V1 antes de un fallo de V2: no se promete rollback conjunto
([transacciones de Flyway](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html)).
Nunca se reintenta automáticamente. Un PASS de migrate exige dos migraciones ejecutadas,
versión 2, cuatro tablas esperadas e historial y checksums conformes después del COMMIT.
Los grants runtime y las verificaciones administrativas posteriores siguen siendo
operaciones separadas. Ninguna cuenta recibe permisos mediante este runner.

## Observación de certificado sin autenticación

El probe opcional utiliza un TrustManager que **siempre rechaza** la cadena. Solo
recoge metadatos públicos presentados durante TLS; no concede confianza ni acredita
un handshake válido, y no utiliza credenciales SQL existentes:

```powershell
java -cp tools/db-runner/target/ferrestock-db-runner-0.0.1-SNAPSHOT.jar pe.edu.cibertec.ferrestock.authreportes.config.MigrationCertificateProbe
```

Esta clase no está en el classpath del backend. Nunca conectarla a un datasource de
aplicación. Un resultado `CERTIFICATE_OBSERVED_UNTRUSTED` mantiene explícitamente
`StrictTlsValidated=false` y `SqlAuthenticated=false`.
