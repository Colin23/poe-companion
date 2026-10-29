-- Personal confirmations are exact contexts, not canonical effective-from-patch publications.
-- Historical revisions remain stored after replacement or ordinary removal.
CREATE TABLE poe_companion.drop_rate_override_revision (
    id UUID NOT NULL,
    account_context_id UUID NOT NULL,
    drop_relationship_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    game_patch_number INTEGER,
    game_patch_suffix VARCHAR(32),
    model_type VARCHAR(32) NOT NULL,
    probability_per_attempt DOUBLE PRECISION NOT NULL,
    status VARCHAR(32) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT pk_drop_rate_override_revision PRIMARY KEY (id),
    CONSTRAINT fk_drop_rate_override_account_context
        FOREIGN KEY (account_context_id) REFERENCES poe_companion.account_context (id),
    CONSTRAINT fk_drop_rate_override_relationship
        FOREIGN KEY (drop_relationship_id) REFERENCES poe_companion.drop_relationship (id),
    CONSTRAINT chk_drop_rate_override_version
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0),
    CONSTRAINT chk_drop_rate_override_patch
        CHECK (
            (game_patch_number IS NULL OR game_patch_number >= 0)
            AND (game_patch_suffix IS NULL OR
                (game_patch_number IS NOT NULL AND BTRIM(game_patch_suffix) <> ''))
        ),
    CONSTRAINT chk_drop_rate_override_model CHECK (model_type = 'FIXED_BERNOULLI'),
    CONSTRAINT chk_drop_rate_override_probability
        CHECK (probability_per_attempt > 0.0 AND probability_per_attempt <= 1.0),
    CONSTRAINT chk_drop_rate_override_status
        CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'REMOVED')),
    CONSTRAINT chk_drop_rate_override_lifecycle
        CHECK (
            (status = 'ACTIVE' AND ended_at IS NULL)
            OR (status IN ('SUPERSEDED', 'REMOVED') AND ended_at IS NOT NULL)
        )
);

-- NULLS NOT DISTINCT is essential: a missing patch and a missing suffix are exact values,
-- not wildcards, and cannot allow duplicate current confirmations.
CREATE UNIQUE INDEX uq_drop_rate_override_active_key
    ON poe_companion.drop_rate_override_revision (
        account_context_id, drop_relationship_id,
        compatibility_version_major, compatibility_version_minor,
        game_patch_number, game_patch_suffix
    ) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
