CREATE TABLE poe_companion.manual_ownership (
    account_context_id UUID NOT NULL,
    unique_definition_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT pk_manual_ownership PRIMARY KEY (account_context_id, unique_definition_id),
    CONSTRAINT fk_manual_ownership_account_context
        FOREIGN KEY (account_context_id)
        REFERENCES poe_companion.account_context (id),
    CONSTRAINT fk_manual_ownership_unique_definition
        FOREIGN KEY (unique_definition_id)
        REFERENCES poe_companion.unique_definition (id),
    CONSTRAINT chk_manual_ownership_quantity CHECK (quantity >= 0)
);
