-- Accepted CAN_DROP/CANNOT_DROP change points are versioned independently of stable relationship identity.
-- These rows represent current curator knowledge, not a history of curator revisions.
CREATE TABLE poe_companion.drop_relationship_knowledge (
    id UUID NOT NULL,
    drop_relationship_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    ruleset VARCHAR(32) NOT NULL,
    league_definition_id UUID,
    effective_patch_number INTEGER,
    effective_patch_suffix VARCHAR(32),
    state VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_drop_relationship_knowledge PRIMARY KEY (id),
    CONSTRAINT fk_drop_relationship_knowledge_relationship
        FOREIGN KEY (drop_relationship_id)
        REFERENCES poe_companion.drop_relationship (id),
    CONSTRAINT fk_drop_relationship_knowledge_league
        FOREIGN KEY (league_definition_id)
        REFERENCES poe_companion.league_definition (id),
    CONSTRAINT chk_drop_relationship_knowledge_version
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0),
    CONSTRAINT chk_drop_relationship_knowledge_ruleset
        CHECK (ruleset IN ('NORMAL', 'RUTHLESS')),
    CONSTRAINT chk_drop_relationship_knowledge_patch
        CHECK (
            (effective_patch_number IS NULL OR effective_patch_number >= 0)
            AND (effective_patch_suffix IS NULL OR (
                effective_patch_number IS NOT NULL AND btrim(effective_patch_suffix) <> ''
            ))
        ),
    CONSTRAINT chk_drop_relationship_knowledge_state
        CHECK (state IN ('CAN_DROP', 'CANNOT_DROP')),
    CONSTRAINT chk_drop_relationship_knowledge_revision
        CHECK (revision >= 0)
);

-- All records are currently accepted; NULLS NOT DISTINCT enforces one baseline/league-independent change point.
CREATE UNIQUE INDEX uq_drop_relationship_knowledge_change_point
    ON poe_companion.drop_relationship_knowledge (
        drop_relationship_id,
        compatibility_version_major,
        compatibility_version_minor,
        ruleset,
        league_definition_id,
        effective_patch_number,
        effective_patch_suffix
    ) NULLS NOT DISTINCT;
