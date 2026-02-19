# Quality Toolkit

Project-level quality checks are centralized in this folder for:

- General code quality checks (backend + frontend)
- Static security code review
- Frontend functional tests runnable post-build
- Frontend performance test tooling for all screens
- API functional and performance tests

## Folder Structure

- `config/` - Checkstyle and PMD rulesets
- `static-security/` - Semgrep static security rules
- `functional/frontend/` - Playwright post-build screen/functionality tests
- `functional/api/` - k6 API functional tests
- `performance/frontend/` - Lighthouse CI config for all pages
- `performance/api/` - k6 API smoke/load tests
- `scripts/` - Cross-platform quality pipeline runners
- `reports/` - Generated quality outputs (git-ignored)

## Prerequisites

At repository root and `frontend/`, install normal project dependencies first.

From `quality/`, install quality tooling:

```bash
npm install
npx playwright install
```

Additional CLI dependencies:

- `k6` (for API functional/performance tests)
- `semgrep` (for static security scan)
- Java + Maven (already needed by backend)

Windows note:

- The repository includes a local k6 binary at `quality/tools/k6/k6-v1.6.1-windows-amd64/k6.exe` used by npm scripts.
- Static scan script uses `pysemgrep.exe` from `%APPDATA%\\Python\\Python313\\Scripts`.

## Environment

Copy `.env.template` values into your shell environment before running API tests when needed:

- `BASE_URL`
- `CLIENT_USER`, `CLIENT_PASSWORD`
- `ADMIN_USER`, `ADMIN_PASSWORD`
- `CLIENT_ID`

## Run Commands

### Windows PowerShell

```powershell
./scripts/run-quality.ps1 -Target all
./scripts/run-quality.ps1 -Target backend
./scripts/run-quality.ps1 -Target frontend-functional
./scripts/run-quality.ps1 -Target api-perf-load
```

### macOS/Linux

```bash
chmod +x ./scripts/run-quality.sh
./scripts/run-quality.sh all
./scripts/run-quality.sh frontend-functional
./scripts/run-quality.sh api-perf-smoke
```

### Direct npm scripts

```bash
npm run quality:backend
npm run quality:frontend
npm run security:static
npm run security:deps
npm run frontend:test:postbuild
npm run frontend:perf
npm run api:functional
npm run api:perf:smoke
npm run api:perf:load
```

## Coverage Implemented

### Frontend functional (post-build)

`functional/frontend/tests/screens.spec.ts` validates:

- Login screen rendering and controls
- Client pages:
  - `/portfolio`
  - `/trade`
  - `/order-history`
  - `/realized-gains`
  - `/unrealized-gains`
  - `/fund-account`
  - `/import-data`
- Admin pages:
  - `/admin/clients`
  - `/admin/rules`
- Core client/admin navigation for major user flows

### Frontend performance

`performance/frontend/lighthouserc.json` runs Lighthouse against all screens above.

### API functional

`functional/api/api-functional.k6.js` validates key API functionality:

- Auth login
- Account, portfolio, summary, trades, stock price, trend, prediction endpoints
- Admin clients/rules/trades endpoints

### API performance

- `performance/api/api-smoke.k6.js` for fast confidence checks
- `performance/api/api-load.k6.js` for staged load profile

## Output Artifacts

- Playwright HTML report: `quality/reports/playwright-report`
- Lighthouse reports: `quality/reports/lighthouse`
- OWASP dependency-check report: `quality/reports/dependency-check`

