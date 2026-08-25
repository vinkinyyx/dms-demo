# DMS Automated Testing System

> Four-layer automated test suite for DMS. Last updated: 2026-08-26.

## Architecture

| Layer | Purpose | Tool | Time | Command |
|-------|---------|------|------|---------|
| L1 | Static analysis: garbled text, ISO dates, console.log, hardcoded URLs | Node.js | ~1s | `npm run lint:static` |
| L2 | API smoke: login + 25 core endpoints return 200 | Python | ~5s | `npm run test:api` |
| L3 | UI smoke: all pages load, Console clean, no 5xx, row/create buttons clickable | Playwright | ~4min | `npm run test:smoke` |
| L4 | Business E2E: full module flows (search, view, create dialog, navigation) | Playwright | ~6min | `npm run test:e2e` |
| All | Run L1-L4 in sequence | Node.js | ~10min | `npm run test:all` |

## Quick Start

```bash
# Run everything (recommended before delivery)
npm run test:all

# Run just one layer
npm run lint:static
npm run test:api
npm run test:smoke
npm run test:e2e

# Test a specific module only
node tools/smoke-test.cjs --module=products
node automation_test/e2e/run-all.js --module=orders

# Specify a different environment
E2E_BASE=http://43.128.145.141 npm run test:smoke  # API/health use root; UI pages use /dms, /dms/admin, /dms/mobile

# Run with visible browser (for debugging)
E2E_HEADED=1 node automation_test/e2e/specs/01-basic-data.spec.js
```

## L4 E2E Coverage (9 spec files, 232 checks)

| Spec | Modules | Checks |
|------|---------|--------|
| 01-basic-data | products, categories, product-lines, product-bundles, dealers, hospitals, warehouses, regions, suppliers, product-prices | 51 |
| 02-orders | sales-orders (wizard), sales-returns, purchase-orders, purchase-returns | 21 |
| 03-inventory | inventory, sales-outs, receipts, stock-moves, adjustments, expiry, stocktakes, traceability | 33 |
| 04-surgery-marketing | surgery-reports, promotions, mobile surgery create form | 13 |
| 05-contracts-auth | contracts, contract-templates, authorizations | 16 |
| 06-reports-dashboard | dashboard, reports center, 6 report types, subscriptions, dealer profile | 21 |
| 07-management | approval flow, users, positions, roles, page configs, logs, notifications | 43 |
| 08-admin-backend | 7 admin pages (tenants, menus, dicts, logs) | 22 |
| 09-mobile | mobile home, approvals, surgery reports, messages | 12 |

Each module test verifies:
- Page loads without 404/blank
- No Console errors or 500 network responses
- Search and reset work
- First row action (view/detail) opens dialog or navigates correctly
- Create button opens form dialog (or correctly absent for read-only pages)

## Adding New Tests

### Add a new module to an existing spec

Edit the corresponding file in `e2e/specs/` and add a line:

```js
await testCrudModule(page, runner, { moduleKey: "your-module-key", label: "PC-your-module", expectCreate: true });
```

Use `route: "/your/route"` instead of `moduleKey` if the page uses a custom route (not `/m/{key}`).

### Create a new spec file

Copy an existing spec as a template. Each spec:
1. Launches a browser
2. Logs in (PC/admin/mobile)
3. Runs test steps using helpers
4. Saves a JSON report to `e2e/results/`
5. Exits with code 1 on failure

### Helper functions

- `helpers/auth.js`: `loginPC()`, `loginAdmin()`, `loginMobile()`
- `helpers/page.js`: `gotoModule()`, `clickFirstRowAction()`, `clickCreateButton()`, `closeDialogs()`, `screenshot()`
- `helpers/crud.js`: `testCrudModule()` - complete CRUD test for a list page
- `helpers/errors.js`: `createErrorCollector()` - captures Console and network errors
- `helpers/runner.js`: `createRunner()` - assertion and reporting

## Test Data

- Tests use the `admin` account on the test environment.
- Read-only tests are safe to run repeatedly.
- Create dialogs are opened but **not submitted** (no test data is written to the database).
- Screenshots and JSON reports are saved to `e2e/results/` (git-ignored).

## Integration with Workflow

Per `AGENTS.md`, delivery self-check requires:

1. L1 static check passes
2. L2 API smoke passes
3. Affected module L3/L4 tests pass
4. No Console errors on affected pages

Run `npm run test:all` before every delivery to catch basic issues automatically.
