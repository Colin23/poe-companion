-- Canonical fixed estimates retain curator corrections without losing their historical game-patch applicability.
-- The source-specific completed-encounter attempt is derived from the referenced DropRelationship.
CREATE TABLE poe_companion.fixed_drop_estimate (
    id UUID NOT NULL,
    drop_relationship_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    ruleset VARCHAR(32) NOT NULL,
    league_definition_id UUID,
    model_type VARCHAR(32) NOT NULL,
    effective_patch_number INTEGER,
    effective_patch_suffix VARCHAR(32),
    probability_per_attempt DOUBLE PRECISION NOT NULL,
    provenance VARCHAR(32) NOT NULL,
    evidence_reference TEXT NOT NULL,
    reviewed_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    superseded_at TIMESTAMPTZ,
    CONSTRAINT pk_fixed_drop_estimate PRIMARY KEY (id),
    CONSTRAINT fk_fixed_drop_estimate_relationship FOREIGN KEY (drop_relationship_id)
        REFERENCES poe_companion.drop_relationship (id),
    CONSTRAINT fk_fixed_drop_estimate_league FOREIGN KEY (league_definition_id)
        REFERENCES poe_companion.league_definition (id),
    CONSTRAINT chk_fixed_drop_estimate_version_non_negative
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0),
    CONSTRAINT chk_fixed_drop_estimate_ruleset CHECK (ruleset IN ('NORMAL', 'RUTHLESS')),
    CONSTRAINT chk_fixed_drop_estimate_model CHECK (model_type = 'FIXED_BERNOULLI'),
    CONSTRAINT chk_fixed_drop_estimate_patch
        CHECK (
            (effective_patch_number IS NULL OR effective_patch_number >= 0)
            AND (effective_patch_suffix IS NULL OR
                (effective_patch_number IS NOT NULL AND btrim(effective_patch_suffix) <> ''))
        ),
    CONSTRAINT chk_fixed_drop_estimate_probability
        CHECK (probability_per_attempt > 0.0 AND probability_per_attempt <= 1.0),
    CONSTRAINT chk_fixed_drop_estimate_provenance
        CHECK (provenance IN ('OFFICIAL', 'MEASURED', 'COMMUNITY_ESTIMATE')),
    CONSTRAINT chk_fixed_drop_estimate_evidence CHECK (btrim(evidence_reference) <> ''),
    CONSTRAINT chk_fixed_drop_estimate_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT chk_fixed_drop_estimate_lifecycle
        CHECK (
            (status = 'ACTIVE' AND superseded_at IS NULL)
            OR (status = 'SUPERSEDED' AND superseded_at IS NOT NULL)
        )
);

-- NULLS NOT DISTINCT prevents multiple ACTIVE baselines or league-independent estimates at the same exact key.
CREATE UNIQUE INDEX uq_fixed_drop_estimate_active_key
    ON poe_companion.fixed_drop_estimate (
        drop_relationship_id,
        compatibility_version_major,
        compatibility_version_minor,
        ruleset,
        league_definition_id,
        model_type,
        effective_patch_number,
        effective_patch_suffix
    ) NULLS NOT DISTINCT
    WHERE status = 'ACTIVE';
