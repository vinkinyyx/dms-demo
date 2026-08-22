import json
import os
import sys

import paramiko


def bigint_list(values):
    cleaned = sorted({int(value) for value in values if value is not None})
    if not cleaned:
        return "ARRAY[]::bigint[]"
    return "ARRAY[" + ",".join(str(value) for value in cleaned) + "]::bigint[]"


payload = json.load(sys.stdin)
order_ids = bigint_list(payload.get("orderIds", []))
out_ids = bigint_list(payload.get("outIds", []))
tenant_code = payload.get("tenantCode", "default")

host = os.environ.get("DMS_DEPLOY_HOST", "43.128.145.141")
user = os.environ.get("DMS_DEPLOY_USER", "ubuntu")
password = os.environ.get("DMS_DEPLOY_PASSWORD")
if not password:
    raise SystemExit("DMS_DEPLOY_PASSWORD is required for E2E DB cleanup")

sql = f"""
BEGIN;
CREATE TEMP TABLE cleanup_orders AS
SELECT o.id
FROM orders o
JOIN tenants t ON t.id = o.tenant_id
WHERE t.code = '{tenant_code}'
  AND o.id = ANY({order_ids});

CREATE TEMP TABLE cleanup_outs AS
SELECT so.id
FROM sales_outs so
JOIN tenants t ON t.id = so.tenant_id
WHERE t.code = '{tenant_code}'
  AND (so.id = ANY({out_ids}) OR so.source_order_id IN (SELECT id FROM cleanup_orders));

CREATE TEMP TABLE cleanup_approvals AS
SELECT ai.id
FROM approval_instances ai
JOIN tenants t ON t.id = ai.tenant_id
WHERE t.code = '{tenant_code}'
  AND ai.business_id IN (SELECT id FROM cleanup_orders);

DELETE FROM approval_records WHERE instance_id IN (SELECT id FROM cleanup_approvals);
DELETE FROM approval_tasks WHERE instance_id IN (SELECT id FROM cleanup_approvals);
DELETE FROM approval_cc_records WHERE instance_id IN (SELECT id FROM cleanup_approvals);
DELETE FROM approval_instances WHERE id IN (SELECT id FROM cleanup_approvals);

DELETE FROM erp_outbound_callbacks WHERE sales_out_id IN (SELECT id FROM cleanup_outs);
DELETE FROM sales_out_execution_lines WHERE sales_out_id IN (SELECT id FROM cleanup_outs);
DELETE FROM sales_out_batch_lines WHERE batch_id IN (SELECT id FROM sales_out_batches WHERE sales_out_id IN (SELECT id FROM cleanup_outs));
DELETE FROM sales_out_batches WHERE sales_out_id IN (SELECT id FROM cleanup_outs);
DELETE FROM sales_out_lines WHERE sales_out_id IN (SELECT id FROM cleanup_outs);
DELETE FROM sales_outs WHERE id IN (SELECT id FROM cleanup_outs);

DELETE FROM order_promotion_hits WHERE order_id IN (SELECT id FROM cleanup_orders);
DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM cleanup_orders);
DELETE FROM order_lines WHERE order_id IN (SELECT id FROM cleanup_orders);
DELETE FROM orders WHERE id IN (SELECT id FROM cleanup_orders);

SELECT
  (SELECT count(*) FROM cleanup_orders) AS deleted_orders,
  (SELECT count(*) FROM cleanup_outs) AS deleted_outs,
  (SELECT count(*) FROM cleanup_approvals) AS deleted_approvals;
COMMIT;
"""

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(
    host,
    port=22,
    username=user,
    password=password,
    timeout=20,
    banner_timeout=30,
    auth_timeout=20,
    allow_agent=False,
    look_for_keys=False,
)
try:
    command = (
        'echo "$DMS_DEPLOY_PASSWORD" | sudo -S -p "" '
        "docker exec -i dms-test-postgres psql -U dms -d dms_test "
        "-v ON_ERROR_STOP=1 -P pager=off <<'SQL'\n"
        + sql
        + "SQL"
    )
    _, stdout, stderr = client.exec_command(
        f"export DMS_DEPLOY_PASSWORD={password!r}; {command}",
        timeout=120,
    )
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.strip())
    if err.strip():
        print(err.strip(), file=sys.stderr)
    if code != 0:
        raise SystemExit(code)
finally:
    client.close()
