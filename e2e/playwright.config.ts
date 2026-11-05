import { defineConfig } from '@playwright/test'

const adminPort = 4173

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 45_000,
  expect: {
    timeout: 8_000
  },
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
  ],
  use: {
    baseURL: `http://127.0.0.1:${adminPort}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  webServer: {
    command: 'node ./mock-static-server.mjs',
    url: `http://127.0.0.1:${adminPort}/__health`,
    reuseExistingServer: !process.env.CI,
    timeout: 30_000
  }
})
