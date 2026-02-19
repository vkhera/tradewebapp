import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  reporter: [['html', { outputFolder: '../../reports/playwright-report', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4201',
    trace: 'on-first-retry'
  },
  webServer: {
    // Custom SPA+proxy server: serves Angular static files with index.html fallback
    // for unknown routes (so Angular Router handles them) and proxies /api/** to
    // the running Spring Boot backend on port 8080.
    command: 'node spa-proxy-server.js 4201',
    url: 'http://127.0.0.1:4201',
    reuseExistingServer: false,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
