CREATE TABLE poe_companion.external_identity (
    provider VARCHAR(32) NOT NULL,
    provider_key VARCHAR(1024) NOT NULL,
    unique_definition_id UUID NOT NULL,
    CONSTRAINT pk_external_identity PRIMARY KEY (provider, provider_key),
    CONSTRAINT ck_external_identity_provider
        CHECK (provider IN ('POE_WIKI', 'REPOE')),
    CONSTRAINT fk_external_identity_unique_definition
        FOREIGN KEY (unique_definition_id)
        REFERENCES poe_companion.unique_definition (id)
);
