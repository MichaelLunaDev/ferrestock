import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
export default defineConfig({
 plugins: [react()],
 server: { port: 5173, strictPort: true, proxy: { '/api': { target: 'http://127.0.0.1:8081', changeOrigin: false } } },
 preview: { port: 4173, strictPort: true },
 test: { environment: 'jsdom', setupFiles: './src/test/setup.ts', clearMocks: true, testTimeout: 15000, exclude: ['node_modules/**', 'dist/**', 'e2e/**'] },
});
