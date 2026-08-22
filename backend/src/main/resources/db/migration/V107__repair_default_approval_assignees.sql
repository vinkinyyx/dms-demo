-- V107: repair missing default approval assignees
-- Root cause: V78 seeded assignees but the table was later emptied (manual cleanup / bad reset script),
-- leaving every default template node without approvers. Submissions failed with
-- "no approver for node ..." and the business document rolled back to DRAFT.
-- This migration is idempotent: for every ENABLED default template node that has no assignee,
-- attach the SYS_ADMIN role (id=1) as the default approver.

DO $$
DECLARE
    v_tid UUID := '11111111-1111-1111-1111-111111111111';
    v_rec RECORD;
BEGIN
    FOR v_rec IN
        SELECT n.id AS node_id, n.tenant_id
        FROM approval_template_nodes n
        JOIN approval_templates t ON t.id = n.template_id
        WHERE t.tenant_id = v_tid
          AND t.status = 'ENABLED'
          AND t.code IN ('PO-DEFAULT','SRT-DEFAULT','PRT-DEFAULT','CT-DEFAULT','AUTH-DEFAULT')
          AND NOT EXISTS (
              SELECT 1 FROM approval_node_assignees a WHERE a.node_id = n.id
          )
    LOOP
        INSERT INTO approval_node_assignees (node_id, tenant_id, assignee_type, ref_id, display_name, created_at)
        VALUES (v_rec.node_id, v_rec.tenant_id, 'ROLE', 1, '系统管理员', now());
    END LOOP;
END $$;
