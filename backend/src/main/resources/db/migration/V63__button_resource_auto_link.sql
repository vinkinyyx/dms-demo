-- V63: trigger — 任何租户新增 type=button 的 rbac_resources 时，自动关联到 strategy_id=1（"全部权限"）
-- 这样 V61 之后即使再补 button 资源，admin 用户（拥有 strategy 1）也能立即获得权限。

CREATE OR REPLACE FUNCTION auto_link_button_to_strategy1() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.type = 'button' AND NEW.deleted_at IS NULL THEN
        INSERT INTO strategy_resources (strategy_id, resource_id, operations, created_at)
        VALUES (1, NEW.id, ARRAY['read','write']::varchar[], now())
        ON CONFLICT DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_resources_button_strategy1 ON resources;
CREATE TRIGGER trg_resources_button_strategy1
AFTER INSERT ON resources
FOR EACH ROW
EXECUTE FUNCTION auto_link_button_to_strategy1();