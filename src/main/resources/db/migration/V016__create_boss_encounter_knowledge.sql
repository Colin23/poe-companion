-- Accepted complete encounter snapshots, independently scoped to source, version, ruleset, league and game patch.
-- These are game-patch change points, not a historical record of curator edits.
CREATE TABLE poe_companion.boss_encounter_knowledge (
    id UUID NOT NULL,
    acquisition_source_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    ruleset VARCHAR(32) NOT NULL,
    league_definition_id UUID,
    effective_patch_number INTEGER,
    effective_patch_suffix VARCHAR(32),
    display_label TEXT NOT NULL,
    availability VARCHAR(32) NOT NULL,
    participation_kind VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_boss_encounter_knowledge PRIMARY KEY (id),
    CONSTRAINT fk_boss_encounter_knowledge_source
        FOREIGN KEY (acquisition_source_id) REFERENCES poe_companion.acquisition_source (id),
    CONSTRAINT fk_boss_encounter_knowledge_league
        FOREIGN KEY (league_definition_id) REFERENCES poe_companion.league_definition (id),
    CONSTRAINT chk_boss_encounter_knowledge_version
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0),
    CONSTRAINT chk_boss_encounter_knowledge_ruleset
        CHECK (ruleset IN ('NORMAL', 'RUTHLESS')),
    CONSTRAINT chk_boss_encounter_knowledge_patch
        CHECK (
            (effective_patch_number IS NULL OR effective_patch_number >= 0)
            AND (effective_patch_suffix IS NULL OR (
                effective_patch_number IS NOT NULL AND btrim(effective_patch_suffix) <> ''
            ))
        ),
    CONSTRAINT chk_boss_encounter_knowledge_label
        CHECK (display_label COLLATE "C" ~ '[^[:space:]\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\u001C-\u001F]'),
    CONSTRAINT chk_boss_encounter_knowledge_availability
        CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT chk_boss_encounter_knowledge_participation
        CHECK (participation_kind IN ('FIXED', 'CONFIGURATION_DEPENDENT')),
    CONSTRAINT chk_boss_encounter_knowledge_revision CHECK (revision >= 0)
);

CREATE UNIQUE INDEX uq_boss_encounter_knowledge_change_point
    ON poe_companion.boss_encounter_knowledge (
        acquisition_source_id,
        compatibility_version_major,
        compatibility_version_minor,
        ruleset,
        league_definition_id,
        effective_patch_number,
        effective_patch_suffix
    ) NULLS NOT DISTINCT;

-- The participant set belongs to an accepted complete snapshot, not to immutable source identity.
CREATE TABLE poe_companion.boss_encounter_participant (
    knowledge_id UUID NOT NULL,
    boss_definition_id UUID NOT NULL,
    CONSTRAINT pk_boss_encounter_participant PRIMARY KEY (knowledge_id, boss_definition_id),
    CONSTRAINT fk_boss_encounter_participant_knowledge
        FOREIGN KEY (knowledge_id) REFERENCES poe_companion.boss_encounter_knowledge (id),
    CONSTRAINT fk_boss_encounter_participant_boss
        FOREIGN KEY (boss_definition_id) REFERENCES poe_companion.boss_definition (id)
);
