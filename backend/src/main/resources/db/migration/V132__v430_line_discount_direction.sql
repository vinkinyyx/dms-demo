-- v4.3.0：持久化行折扣方向（REDUCE 折扣减 / ADD 加价高开），提交重算时还原。
ALTER TABLE order_lines ADD COLUMN IF NOT EXISTS line_discount_direction VARCHAR(8);
