// Error collector - attaches console and network listeners to a page
function createErrorCollector() {
  const consoleErrors = [];
  const networkErrors = [];
  const pageErrors = [];

  function attach(page, label) {
    page.on("console", (msg) => {
      if (msg.type() === "error") {
        const text = msg.text();
        if (/favicon|ResizeObserver|Download is prohibited|net::ERR_ABORTED/i.test(text)) return;
        consoleErrors.push({ page: label, text: text.slice(0, 500) });
      }
    });
    page.on("pageerror", (err) => {
      pageErrors.push({ page: label, text: err.message.slice(0, 500) });
    });
    page.on("requestfailed", (req) => {
      const err = (req.failure() || {}).errorText || "";
      if (/favicon/i.test(req.url())) return;
      networkErrors.push({ page: label, url: req.url().slice(0, 250), err: err.slice(0, 200) });
    });
    page.on("response", (res) => {
      if (res.status() >= 400) {
        networkErrors.push({ page: label, url: res.url().slice(0, 250), status: res.status() });
      }
    });
  }

  function drain(label) {
    const ce = consoleErrors.filter((e) => e.page === label);
    const ne = networkErrors.filter((e) => e.page === label && e.status >= 400);
    const pe = pageErrors.filter((e) => e.page === label);
    for (let i = consoleErrors.length - 1; i >= 0; i--) if (consoleErrors[i].page === label) consoleErrors.splice(i, 1);
    for (let i = networkErrors.length - 1; i >= 0; i--) if (networkErrors[i].page === label) networkErrors.splice(i, 1);
    for (let i = pageErrors.length - 1; i >= 0; i--) if (pageErrors[i].page === label) pageErrors.splice(i, 1);
    return { consoleErrors: ce, serverErrors: ne, pageErrors: pe };
  }

  function all() {
    return { consoleErrors: [...consoleErrors], serverErrors: networkErrors.filter(e => e.status >= 400), pageErrors: [...pageErrors] };
  }

  function format(label) {
    const e = drain(label);
    const lines = [];
    if (e.pageErrors.length) lines.push("JS errors: " + e.pageErrors.map(x => x.text).join("; "));
    if (e.consoleErrors.length) lines.push("Console errors: " + e.consoleErrors.map(x => x.text).join("; "));
    if (e.serverErrors.length) lines.push("HTTP errors: " + e.serverErrors.map(x => x.status + " " + x.url).join("; "));
    return lines.join(" | ");
  }

  return { attach, drain, all, format };
}

module.exports = { createErrorCollector };
