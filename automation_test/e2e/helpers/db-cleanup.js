const { spawnSync } = require('child_process');
const path = require('path');

function cleanupTestArtifacts({ orderIds = [], outIds = [], tenantCode = 'default' } = {}) {
  const normalizedOrderIds = [...new Set(orderIds.filter(Boolean).map(Number).filter(Number.isInteger))];
  const normalizedOutIds = [...new Set(outIds.filter(Boolean).map(Number).filter(Number.isInteger))];
  if (!normalizedOrderIds.length && !normalizedOutIds.length) return;
  if (!process.env.DMS_DEPLOY_PASSWORD) {
    console.warn('Skip E2E DB cleanup: DMS_DEPLOY_PASSWORD is not set');
    return;
  }
  const result = spawnSync(
    process.platform === 'win32' ? 'python' : 'python3',
    [path.join(__dirname, 'db-cleanup.py')],
    {
      input: JSON.stringify({ orderIds: normalizedOrderIds, outIds: normalizedOutIds, tenantCode }),
      encoding: 'utf8',
      env: process.env,
      timeout: 120000,
    },
  );
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.status !== 0) {
    throw new Error(`E2E DB cleanup failed with status ${result.status}`);
  }
}

module.exports = { cleanupTestArtifacts };
