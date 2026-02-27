import { defineConfig, devices } from '@playwright/test';

// When PLAYWRIGHT_BASE_URL env var is set (e.g. when running against Docker on
// port 80), use it directly and skip the webServer spin-up.
const DOCKER_URL  = process.env['PLAYWRIGHT_BASE_URL'];
// Optional HTTPS target (e.g. https://localhost). Set PLAYWRIGHT_HTTPS_URL to
// enable an additional test project that exercises the SSL/nginx layer.
const HTTPS_URL   = process.env['PLAYWRIGHT_HTTPS_URL'];

export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  expect: {
    timeout: 10_000
  },
  fullyParallel: false,
  workers: 1,
  reporter: [['html', { outputFolder: '../../reports/playwright-report', open: 'never' }]],
  use: {
    baseURL: DOCKER_URL ?? 'http://127.0.0.1:4201',
    // Accept self-signed certificates used by the Docker nginx SSL setup
    ignoreHTTPSErrors: true,
    trace: 'on-first-retry'
  },
  webServer: DOCKER_URL ? undefined : {
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
    },
    // HTTPS project — only included when PLAYWRIGHT_HTTPS_URL is set
    // Run with: PLAYWRIGHT_HTTPS_URL=https://localhost npx playwright test
    ...( HTTPS_URL ? [{
      name: 'chromium-https',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: HTTPS_URL,
        ignoreHTTPSErrors: true
      }
    }] : [])
  ]
});
