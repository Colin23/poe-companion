-- A personal PoE context must have one stable owner/league/ruleset identity.
-- Abort rather than silently merge existing duplicates and their separate personal state.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM poe_companion.account_context
        GROUP BY owner_application_user_id, league_definition_id, ruleset
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate account contexts for one owner/league/ruleset; resolve them before applying V019';
    END IF;
END
$$;

ALTER TABLE poe_companion.account_context
    ADD CONSTRAINT uq_account_context_owner_league_ruleset
        UNIQUE (owner_application_user_id, league_definition_id, ruleset);

-- The previous per-user selection required ownership of an AccountContext.
-- Preserve its exact version and patch while moving the selected league/ruleset
-- into the selection itself; no AccountContext identity or personal state is changed.
ALTER TABLE poe_companion.account_evaluation_selection
    ADD COLUMN league_definition_id UUID,
    ADD COLUMN ruleset VARCHAR(32);

UPDATE poe_companion.account_evaluation_selection AS selection
SET league_definition_id = account.league_definition_id,
    ruleset = account.ruleset
FROM poe_companion.account_context AS account
WHERE selection.account_context_id = account.id
  AND selection.application_user_id = account.owner_application_user_id;

ALTER TABLE poe_companion.account_evaluation_selection
    ALTER COLUMN league_definition_id SET NOT NULL,
    ALTER COLUMN ruleset SET NOT NULL,
    DROP CONSTRAINT fk_account_evaluation_selection_owned_context,
    DROP COLUMN account_context_id,
    ADD CONSTRAINT fk_account_evaluation_selection_league
        FOREIGN KEY (league_definition_id) REFERENCES poe_companion.league_definition (id),
    ADD CONSTRAINT chk_account_evaluation_selection_ruleset
        CHECK (ruleset IN ('NORMAL', 'RUTHLESS'));
