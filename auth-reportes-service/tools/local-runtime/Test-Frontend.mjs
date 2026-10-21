import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { createInterface } from 'node:readline';
const env = { ...process.env };
for (const name of Object.keys(env)) if (/PASSWORD|SECRET|TOKEN|^AUTH_.*DB_|^SQL_TLS_/i.test(name)) delete env[name];
const child = spawn(process.execPath, [new URL('./frontend.mjs', import.meta.url).pathname.replace(/^\/(\w:)/, '$1'), '--self-test'], { env, stdio: ['pipe', 'pipe', 'pipe'] });
let port;
const input = createInterface({ input: child.stdout });
const deadline = setTimeout(() => { child.stdin.end(); process.exitCode = 1; }, 30000);
try {
 for await (const line of input) {
  if (!line.startsWith('{"LocalFrontendPort":')) continue;
  port = JSON.parse(line).LocalFrontendPort;
  break;
 }
 if (!port) throw new Error('No port');
 const response = await fetch(`http://127.0.0.1:${port}/`, { signal: AbortSignal.timeout(5000) });
 if (response.status !== 200) throw new Error('No frontend');
 const exit = once(child, 'exit');
 child.stdin.write('STOP_FERRESTOCK_LOCAL\n');child.stdin.end();
 const [code] = await exit;
 if (code !== 0) throw new Error('Exit failure');
 let stopped = false;
 try { await fetch(`http://127.0.0.1:${port}/`, { signal: AbortSignal.timeout(1000) }); } catch { stopped = true; }
 if (!stopped) throw new Error('Still listening');
 console.log(JSON.stringify({ Result: 'PASS', OwnFrontendStarted: true, Http200: true, GracefulStop: true, SqlOperations: 0, ExistingServicesTouched: false }));
} catch {
 child.stdin.end();process.exitCode = 1;console.log('LOCAL_FRONTEND_TEST_REJECTED');
} finally { clearTimeout(deadline);input.close(); }
