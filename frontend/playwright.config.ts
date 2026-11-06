import { defineConfig } from '@playwright/test'
const port = Number(process.env.MEDICAL_FRONTEND_TEST_PORT ?? 4173)
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1, timeout: 45000,
  use: { baseURL: `http://127.0.0.1:${port}`, browserName: 'chromium', channel: process.env.MEDICAL_BROWSER_CHANNEL || undefined, screenshot: 'only-on-failure' },
  webServer: { command: `node node_modules/vite/bin/vite.js --host 127.0.0.1 --port ${port} --strictPort`, url: `http://127.0.0.1:${port}`, reuseExistingServer: false, timeout: 30000 }
})
