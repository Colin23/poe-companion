-- Preserve every immutable numeric publication while admitting explicit withdrawal change points.
-- Both kinds share the existing ACTIVE exact-key uniqueness and supersession lifecycle.
ALTER TABLE poe_companion.fixed_drop_estimate
    ADD COLUMN knowledge_kind VARCHAR(16) NOT NULL DEFAULT 'ESTIMATE',
    ADD COLUMN withdrawal_reason TEXT;

ALTER TABLE poe_companion.fixed_drop_estimate
    ALTER COLUMN probability_per_attempt DROP NOT NULL,
    ALTER COLUMN provenance DROP NOT NULL;

ALTER TABLE poe_companion.fixed_drop_estimate
    ADD CONSTRAINT chk_fixed_probability_knowledge_kind
        CHECK (knowledge_kind IN ('ESTIMATE', 'WITHDRAWAL')),
    ADD CONSTRAINT chk_fixed_probability_knowledge_payload
        CHECK (
            (knowledge_kind = 'ESTIMATE'
                AND probability_per_attempt IS NOT NULL
                AND provenance IS NOT NULL
                AND withdrawal_reason IS NULL)
            OR
            (knowledge_kind = 'WITHDRAWAL'
                AND probability_per_attempt IS NULL
                AND provenance IS NULL
                AND withdrawal_reason IS NOT NULL
                AND withdrawal_reason COLLATE "C" ~
                    '[^[:space:]\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\u001C-\u001F]')
        );

-- The original V013 active-key index already excludes all duplicate active publications,
-- regardless of kind, because the model_type/scope/patch are identical for both variants.
