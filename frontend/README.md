# Frontend de FerreStock

Aplicación React compartida del proyecto, trasladada desde
`auth-reportes-service/frontend/`. Actualmente incluye autenticación, perfil,
administración de usuarios y reportes. Ventas, Inventario y Chatbot se incorporarán
a esta aplicación cuando sus contratos estén definidos.

Desde `C:\xampp\htdocs\ferrestock\frontend`, con Node >=22.12.0:

```powershell
npm.cmd ci
npm.cmd run dev
```

Abrir http://127.0.0.1:5173. El proxy de desarrollo conserva `/api` hacia
http://127.0.0.1:8081; mover la carpeta no cambia el puerto del backend.
El backend requiere integración SQL autorizada para ofrecer login real.

```powershell
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run test:browser
```

Las pruebas de navegador interceptan la API exclusivamente en `e2e/`, sin SQL.
Sus capturas y resultados permanecen en `frontend/test-results/` (ignorados por Git). `dist/` y
`node_modules/` están ignorados. `preview` sirve archivos estáticos; producción
necesita un proxy `/api` y HTTPS revisados.

`VITE_*` es configuración pública del bundle: nunca introducir secretos.
La sesión mantiene el token solo en memoria. Las futuras pantallas deben usar
el contexto de sesión y cliente HTTP existentes y exigir autorización también
en sus respectivos backends.

La revisión de interfaz está documentada en [UX-01](docs/FRONTEND-UX-01-REPORT.md) y [UX-02 y cierre 02A](docs/FRONTEND-UX-02-REPORT.md). Las evidencias operativas privadas del backend no forman parte de estos informes.
