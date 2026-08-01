-- V43: add product_type to products (frontend product form uses dict product_type).
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS product_type VARCHAR(32);
