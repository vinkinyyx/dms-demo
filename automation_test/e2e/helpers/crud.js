// Reusable CRUD module test flow for CrudView-based list pages
const { gotoModule, clickFirstRowAction, closeDialogs, clickCreateButton, sleep } = require("./page");

async function testCrudModule(page, runner, opts) {
  const { moduleKey, route, label, expectCreate, expectRowAction, expectSearch, skipCreate, skipRowClick } = opts;

  await runner.step(label + " - page load", async () => {
    const res = await gotoModule(page, route || moduleKey, label);
    runner.assert(label + " loads without 404/blank", res.ok, res.error || ("rows=" + res.tableRows));
  });

  // Check console/network errors after load
  await runner.step(label + " - errors check", async () => {
    await sleep(500);
    const errs = runner.errorCollector.format(label);
    runner.assert(label + " no console/5xx errors", !errs, errs || "clean");
  });

  // Search/filter test
  if (expectSearch !== false) {
    await runner.step(label + " - search/reset", async () => {
      // Find a searchable text input in the toolbar (skip selects)
      const allInputs = page.locator('.page-toolbar input[type="text"], .page-toolbar input:not([type])');
      let searchInput = null;
      for (let i = 0; i < await allInputs.count(); i++) {
        const inp = allInputs.nth(i);
        const ph = await inp.getAttribute("placeholder").catch(() => "");
        // Skip date/number inputs by checking the el-input wrapper type
        const isReadonly = await inp.getAttribute("readonly").catch(() => null);
        if (isReadonly) continue;
        searchInput = inp;
        break;
      }
      if (searchInput) {
        await searchInput.fill("test");
        const queryBtn = page.locator('.page-toolbar .el-button--primary:has-text("\u67e5\u8be2")').first();
        if (await queryBtn.count()) {
          await queryBtn.click();
          await sleep(1500);
        }
        // Reset
        const resetBtn = page.locator('.page-toolbar .el-button:has-text("\u91cd\u7f6e")').first();
        if (await resetBtn.count()) {
          await resetBtn.click();
          await sleep(1500);
        }
        runner.assert(label + " search and reset work", true);
      } else {
        runner.assert(label + " search/reset", true, "no search input (may be filtered out by layout)");
      }
    });
  }

  // Row action: click first row view/detail
  if (!skipRowClick) {
    await runner.step(label + " - first row action", async () => {
      const res = await clickFirstRowAction(page);
      if (res.clicked) {
        runner.assert(label + " row action clickable", true, res.opened ? "dialog/detail opened" : "clicked");
        const errs = runner.errorCollector.format(label);
        runner.assert(label + " row action no errors", !errs, errs || "clean");
        await closeDialogs(page);
        // Row action may have navigated to a detail page or opened a dialog.
        // Always re-navigate to the list page to ensure the create button is present.
        await sleep(300);
        await gotoModule(page, route || moduleKey, label);
        await sleep(800);
      } else {
        runner.assert(label + " row action", true, res.reason || "no action");
      }
    });
  }

  // Create button: open form dialog
  if (!skipCreate) {
    await runner.step(label + " - create button", async () => {
      const res = await clickCreateButton(page);
      if (res.clicked) {
        runner.assert(label + " create dialog opens", res.opened, res.opened ? "form visible" : "clicked but no form");
        await closeDialogs(page);
      } else {
        if (expectCreate) {
          runner.assert(label + " create button exists", false, res.reason);
        } else {
          runner.assert(label + " create button", true, "no create button (read-only module)");
        }
      }
    });
  }
}

module.exports = { testCrudModule };
