import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
 testDir: './e2e', fullyParallel: false, workers: 1, reporter: 'list',
 outputDir: './test-results/browser',
 use: { baseURL: 'http://127.0.0.1:5189', trace: 'off', screenshot: 'only-on-failure' },
 projects: [{ name: 'desktop', testMatch: 'panel.spec.ts', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1000 } } }, { name: 'mobile', testMatch: 'panel.spec.ts', use: { ...devices['iPhone 13'], browserName: 'chromium' } }, { name: 'ux', testMatch: 'ux.spec.ts', use: { ...devices['Desktop Chrome'] } }],
 webServer: { command: 'npm run dev -- --port 5189', url: 'http://127.0.0.1:5189', reuseExistingServer: false, timeout: 30000 },
});
