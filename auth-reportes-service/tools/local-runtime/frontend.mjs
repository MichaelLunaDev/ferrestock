import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { createInterface } from 'node:readline';
const root = resolve(import.meta.dirname, '../../../frontend');
const require = createRequire(resolve(root, 'package.json'));
const { createServer } = await import(pathToFileURL(require.resolve('vite')).href);
if (process.argv.slice(2).some(arg => arg !== '--self-test') || process.argv.length > 3) throw new Error('Arguments rejected');
const selfTest = process.argv[2] === '--self-test';
const server = await createServer({ root, server: { host: '127.0.0.1', port: selfTest ? 0 : 5173, strictPort: true } });
await server.listen();
if (selfTest) console.log(JSON.stringify({ LocalFrontendPort: server.httpServer.address().port }));
const input = createInterface({ input: process.stdin });
let closing = false;
async function close() {
 if (closing) return;
 closing = true;
 input.close();
 await server.close();
 process.exitCode = 0;
}
input.on('line', line => { if (line === 'STOP_FERRESTOCK_LOCAL') void close(); });
input.on('close', () => { void close(); });
process.on('SIGINT', () => { void close(); });
process.on('SIGTERM', () => { void close(); });
