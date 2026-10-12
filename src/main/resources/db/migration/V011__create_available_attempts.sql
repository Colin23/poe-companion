-- Explicit zero is a recorded player fact; an absent row means no count has been entered.
CREATE TABLE poe_companion.available_attempts (
    account_context_id UUID NOT NULL,
    acquisition_source_id UUID NOT NULL,
    attempt_count INTEGER NOT NULL,
    CONSTRAINT pk_available_attempts PRIMARY KEY (account_context_id, acquisition_source_id),
    CONSTRAINT fk_available_attempts_account_context
        FOREIGN KEY (account_context_id)
        REFERENCES poe_companion.account_context (id),
    CONSTRAINT fk_available_attempts_acquisition_source
        FOREIGN KEY (acquisition_source_id)
        REFERENCES poe_companion.acquisition_source (id),
    CONSTRAINT chk_available_attempts_non_negative CHECK (attempt_count >= 0)
);
