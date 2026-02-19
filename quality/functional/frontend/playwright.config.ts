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
    command: 'npx http-server ../frontend/dist/stock-brokerage-ui/browser -p 4201 -s',
    url: 'http://127.0.0.1:4201',
    reuseExistingServer: true,
    timeout: 120_000
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
});
