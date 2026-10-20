# FerreStock — Auth/Reportes

Estado actual: backend Auth/Reportes y frontend compartido implementados y
probados offline. El frontend está en [`../frontend/`](../frontend/README.md).
La integración SQL real sigue pendiente: TLS estricto, A4/51100, runner y
migraciones; los reportes comerciales no están activados y conservan 503.
El código incluye autenticación con sesiones opacas, usuarios/roles y exportación
PDF/XLSX; su integración real con SQL Server permanece pendiente.

La documentación detallada de integración, seguridad, contratos HTTP y etapas
históricas se conserva en el entorno de desarrollo del equipo y no forma parte
de esta publicación pública.

## Antecedentes F1/F2B-002A (histórico)

Aplicación Spring Boot independiente. F1 implementa solamente la base ejecutable,
un endpoint de salud y una política de seguridad cerrada por defecto.

F2B-002A añade preparación JDBC, modelos, repositorios, utilidades criptográficas
y migraciones sin ejecutar. Esa etapa todavía no habilitaba autenticación HTTP;
esta descripción corresponde al incremento histórico, no al código actual.

## Requisitos

- JDK 21 y `JAVA_HOME` apuntando al JDK.
- PowerShell y acceso a Maven Central para la primera descarga.
- Maven Wrapper incluido: no es necesario instalar Maven.
- F1 no necesita SQL Server, Docker ni ventas-service en ejecución.

Java 21 y Spring Boot 4.1.1 están alineados con el POM de Ventas.
El wrapper se copió sin cambios de Ventas: wrapper 3.3.4, Maven 3.9.16.

## Compilar y ejecutar en Windows

Ejecutar desde esta carpeta (no desde la raíz del repositorio):

Sin perfiles SQL, el arranque mantiene la base F1 cerrada descrita abajo;
no habilita el login real ni los reportes comerciales.

```powershell
cd C:\xampp\htdocs\ferrestock\auth-reportes-service
java -version
.\mvnw.cmd -B verify
.\mvnw.cmd spring-boot:run
```

También se puede ejecutar el artefacto generado:

```powershell
java -jar target/auth-reportes-service-0.0.1-SNAPSHOT.jar
```

Detener con Ctrl+C. Para consultar desde otra terminal:

```powershell
curl.exe -i http://localhost:8081/api/health
curl.exe -i http://localhost:8081/api/usuarios
```

El primer GET responde 200 con:

```json
{"status":"UP","service":"auth-reportes-service"}
```

El segundo responde 403. Health solo confirma que este proceso atiende HTTP;
no verifica disponibilidad de futuros servicios ni de una base de datos.

## Configuración

| Variable | Predeterminado | Uso |
|---|---|---|
| `PORT` | `8081` | Puerto HTTP de este servicio |

Opcionalmente copiar `.env.example` a `.env`. Spring importa el archivo como
properties desde el directorio de trabajo. No es un script de shell.
Las variables del proceso tienen prioridad sobre el archivo.

```powershell
Copy-Item .env.example .env
$env:PORT = '8082'
.\mvnw.cmd spring-boot:run
Remove-Item Env:PORT
```

El cambio de variable solo afecta a esa terminal y sus procesos. No modifica Ventas.
No hay credenciales ni configuración de datasource en F1.
En la preparación F2B, solo `auth-db` o `auth-integration` activan el datasource
explícito; requieren variables separadas y comprueban identidad con una consulta
de lectura al arrancar. No activar esos perfiles hasta la autorización operativa.
El build ordinario y las pruebas sin perfil SQL no requieren base de datos.
`.env`, `.env.*`, claves privadas, almacenes de claves, logs y `target/` se ignoran;
`.env.example` se conserva versionable.

## Estructura inicial (histórico F1)

```text
src/main/java/pe/edu/cibertec/ferrestock/authreportes/
  FerrestockAuthReportesApplication.java
  controller/HealthController.java
  security/SecurityConfig.java
src/main/resources/application.yaml
src/test/java/pe/edu/cibertec/ferrestock/authreportes/ApplicationSecurityTest.java
```

No se crean capas vacías. Servicios, DTOs y repositorios se añadirán cuando exista
lógica que los necesite. No hay POM agregador ni dependencia del código de Ventas.

## Seguridad inicial (histórico F1)

- Solo `GET /api/health` está permitido anónimamente.
- Toda otra ruta o método se deniega, incluso rutas inexistentes y `/error`.
- Se desactiva la creación automática de usuarios de Spring Boot.
- Basic Auth, formulario, logout y caché de peticiones están deshabilitados.
- No se crean sesiones HTTP. CSRF se deshabilita solo para esta base que
  permite exclusivamente un GET sin credenciales y deniega toda escritura.
  Debe reevaluarse al introducir autenticación, especialmente con cookies.
- La respuesta 403 es provisional para este servicio cerrado. No representa
  el contrato de autenticación 401/403 de una futura implementación.
- No se habilita acceso CORS entre orígenes en F1.

La exclusión de `UserDetailsServiceAutoConfiguration` evita usuarios generados;
no reemplaza la autenticación futura. Las pruebas comprueban su ausencia.

## Pruebas

`ApplicationSecurityTest` arranca el servidor real en un puerto efímero mediante
`PORT=0`, desactiva la importación de `.env` en el contexto de prueba y realiza
peticiones HTTP. Comprueba el JSON exacto de salud, ausencia de datasource y
usuarios automáticos, rechazo de rutas/métodos y de credenciales Basic, sin
redirecciones de login ni cookies de sesión. No requiere una base real.

```powershell
.\mvnw.cmd -B verify
```

Los resultados se escriben en `target/surefire-reports/` (ignorado por Git).

## Dependencias y referencias iniciales (histórico F1/F2B-002A)

F1 utiliza los starters `webmvc`, `security`, `webmvc-test` y `security-test`.
F2B-002A agrega `jdbc` y `mssql-jdbc` runtime. Flyway está exclusivamente en el
perfil Maven `db-migration`, con `skip=true` y sin ejecuciones del ciclo normal;
no ejecutarlo contra SQL Server en este incremento.
El parent 4.1.1 gestiona las versiones; Spring Security corresponde a 7.1.1
según el BOM consultado. No se necesita Bean Validation en este incremento,
porque no hay entradas de negocio.

- [Versiones gestionadas por Spring Boot](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
- [Configuración de seguridad y usuario automático](https://docs.spring.io/spring-boot/reference/web/spring-security.html)
- [Autorización HTTP](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- [Licencia Apache-2.0 de Spring Boot](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt)
- [Licencia Apache-2.0 de Spring Security](https://github.com/spring-projects/spring-security/blob/main/LICENSE.txt)

## Pendientes registrados en F2B-002A (histórico)

El siguiente registro describe lo pendiente en aquella etapa. El código de
autenticación, revocación, usuarios, PDF/XLSX y React se implementó después;
la integración SQL y la activación comercial siguen pendientes actualmente.

No se implementan login, logout, usuarios/roles persistidos, sesiones,
introspección, consultas comerciales, PDF, XLSX, React, Watson ni despliegue.
No se incorporan JPA, JWT, Spring Session ni Redis. JDBC y el driver SQL Server
están preparados; las migraciones y concesiones quedan pendientes de autorización.

DEC-001 prevé dos conexiones independientes: Auth podrá escribir únicamente
en objetos propios; Reportes tendrá otra cuenta con SELECT restringido sobre
tablas o vistas acordadas. No se inicializa ninguna conexión ni esquema en F1.

DEC-002 mantiene catálogo/chatbot públicos y CLIENTE fuera del login del MVP.
No se asocia automáticamente Cliente comercial con un futuro Usuario.

DEC-003 requiere revocación inmediata en todas las APIs protegidas.
F2B-001 y F2B-002A adoptan sesiones opacas en SQL Server; el modelo y los
repositorios están preparados, pero la revocación funcional aún no está implementada.
La introspección central para otros servicios sigue pendiente de contrato y revisión.
La integración futura requerirá aprobación del responsable de Ventas para
validar identidad/permisos en sus rutas y atribuir vendedor desde el backend.
Este servicio no protege automáticamente a ventas-service.
