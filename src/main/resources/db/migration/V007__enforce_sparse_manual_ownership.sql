DELETE FROM poe_companion.manual_ownership
WHERE quantity = 0;

ALTER TABLE poe_companion.manual_ownership
    DROP CONSTRAINT chk_manual_ownership_quantity;

ALTER TABLE poe_companion.manual_ownership
    ADD CONSTRAINT chk_manual_ownership_quantity CHECK (quantity > 0);
