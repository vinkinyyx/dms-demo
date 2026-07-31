-- V40: normalize legacy sales-out code prefix XS -> GI (requirement: sales-out numbers start with GI)
BEGIN;

-- 1) parent sales_outs: XS-YYYYMMDD-NNNNN -> GI-YYYYMMDD-NNNNN
CREATE TEMP TABLE so_xs_map AS
SELECT id, code AS old_code, 'GI' || substring(code from 3) AS new_code
FROM sales_outs
WHERE code LIKE 'XS-2026%';

-- Avoid unique collision: if a GI target code already exists (not an XS row),
-- push it to a fresh number beyond the current day maximum first.
WITH maxes AS (
  SELECT split_part(code,'-',2) AS d,
         MAX(CAST(split_part(code,'-',3) AS integer)) AS mx
  FROM sales_outs
  WHERE code ~ '^(GI|XS)-2026[0-9]{4}-[0-9]{5}$'
  GROUP BY 1
),
conflicts AS (
  SELECT so.id, so.code, m.d,
         'GI-'||m.d||'-'||lpad((m.mx + row_number() OVER (PARTITION BY m.d ORDER BY so.id))::text,5,'0') AS repl
  FROM sales_outs so
  JOIN so_xs_map mp ON mp.new_code = so.code AND mp.id <> so.id
  JOIN maxes m ON m.d = split_part(so.code,'-',2)
)
UPDATE sales_outs so SET code = c.repl
FROM conflicts c
WHERE so.id = c.id;

UPDATE sales_outs so
SET code = mp.new_code
FROM so_xs_map mp
WHERE so.id = mp.id;

-- 2) child sales_out_batches: XS-...-N -> GI-...-N (keep batch suffix)
UPDATE sales_out_batches
SET code = 'GI' || substring(code from 3)
WHERE code LIKE 'XS-2026%';

-- 3) doc_no_sequences: merge XS sequence into GI per day, drop XS row
INSERT INTO doc_no_sequences (tenant_id, prefix, date_key, last_seq)
SELECT tenant_id, 'GI', date_key, MAX(last_seq)
FROM (
  SELECT tenant_id, date_key, last_seq FROM doc_no_sequences WHERE prefix = 'GI'
  UNION ALL
  SELECT tenant_id, date_key, last_seq FROM doc_no_sequences WHERE prefix = 'XS'
  UNION ALL
  SELECT tenant_id, split_part(code,'-',2) AS date_key,
         MAX(CAST(split_part(code,'-',3) AS integer)) AS last_seq
  FROM sales_outs
  WHERE code ~ '^GI-2026[0-9]{4}-[0-9]{5}$'
  GROUP BY tenant_id, split_part(code,'-',2)
) u
GROUP BY tenant_id, date_key
ON CONFLICT (tenant_id, prefix, date_key)
DO UPDATE SET last_seq = GREATEST(doc_no_sequences.last_seq, EXCLUDED.last_seq);

DELETE FROM doc_no_sequences WHERE prefix = 'XS';

COMMIT;