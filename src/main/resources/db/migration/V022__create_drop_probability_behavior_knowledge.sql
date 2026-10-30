-- Curator-confirmed probability-behavior knowledge is independent from CAN_DROP and numeric fixed estimates.
-- Unknown behavior means no unambiguous applicable knowledge, never a persisted UNKNOWN label.
CREATE TABLE poe_companion.drop_probability_behavior_knowledge (
    id UUID NOT NULL,
    drop_relationship_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    ruleset VARCHAR(32) NOT NULL,
    league_definition_id UUID,
    effective_patch_number INTEGER,
    effective_patch_suffix VARCHAR(32),
    behavior VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_drop_probability_behavior_knowledge PRIMARY KEY (id),
    CONSTRAINT fk_drop_probability_behavior_relationship
        FOREIGN KEY (drop_relationship_id) REFERENCES poe_companion.drop_relationship (id),
    CONSTRAINT fk_drop_probability_behavior_league
        FOREIGN KEY (league_definition_id) REFERENCES poe_companion.league_definition (id),
    CONSTRAINT chk_drop_probability_behavior_version
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0),
    CONSTRAINT chk_drop_probability_behavior_ruleset
        CHECK (ruleset IN ('NORMAL', 'RUTHLESS')),
    CONSTRAINT chk_drop_probability_behavior_patch
        CHECK (
            (effective_patch_number IS NULL OR effective_patch_number >= 0)
            AND (effective_patch_suffix IS NULL OR (
                effective_patch_number IS NOT NULL AND btrim(effective_patch_suffix) <> ''
            ))
        ),
    CONSTRAINT chk_drop_probability_behavior_kind
        CHECK (behavior IN ('FIXED_INDEPENDENT', 'MODIFIER_DEPENDENT')),
    CONSTRAINT chk_drop_probability_behavior_revision CHECK (revision >= 0)
);

-- Distinguish exact authored change-point identity even when league or patch is unspecified.
CREATE UNIQUE INDEX uq_drop_probability_behavior_change_point
    ON poe_companion.drop_probability_behavior_knowledge (
        drop_relationship_id,
        compatibility_version_major,
        compatibility_version_minor,
        ruleset,
        league_definition_id,
        effective_patch_number,
        effective_patch_suffix
    ) NULLS NOT DISTINCT;
