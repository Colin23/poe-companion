-- Two temporary configured login identities are stable local UUIDs, not usernames or OAuth subjects.
CREATE TABLE poe_companion.application_identity (
    id UUID NOT NULL,
    CONSTRAINT pk_application_identity PRIMARY KEY (id)
);

INSERT INTO poe_companion.application_identity (id)
VALUES
    ('00000000-0000-4000-8000-000000006001'),
    ('00000000-0000-4000-8000-000000006002');

-- Every pre-existing personalized context (not only the selected one) belongs to the legacy USER.
ALTER TABLE poe_companion.account_context
    ADD COLUMN owner_application_user_id UUID;

UPDATE poe_companion.account_context
SET owner_application_user_id = '00000000-0000-4000-8000-000000006001';

ALTER TABLE poe_companion.account_context
    ALTER COLUMN owner_application_user_id SET NOT NULL,
    ADD CONSTRAINT fk_account_context_owner
        FOREIGN KEY (owner_application_user_id) REFERENCES poe_companion.application_identity (id),
    ADD CONSTRAINT uq_account_context_id_owner UNIQUE (id, owner_application_user_id);

-- Retain all evaluation settings, including an optional patch. Constrain selection ownership in PostgreSQL.
CREATE TABLE poe_companion.account_evaluation_selection_by_user (
    application_user_id UUID NOT NULL,
    account_context_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    game_patch INTEGER,
    game_patch_suffix VARCHAR(32),
    CONSTRAINT pk_account_evaluation_selection_by_user PRIMARY KEY (application_user_id),
    CONSTRAINT fk_account_evaluation_selection_user
        FOREIGN KEY (application_user_id) REFERENCES poe_companion.application_identity (id),
    CONSTRAINT fk_account_evaluation_selection_owned_context
        FOREIGN KEY (account_context_id, application_user_id)
        REFERENCES poe_companion.account_context (id, owner_application_user_id),
    CONSTRAINT chk_selection_major_non_negative CHECK (compatibility_version_major >= 0),
    CONSTRAINT chk_selection_minor_non_negative CHECK (compatibility_version_minor >= 0),
    CONSTRAINT chk_selection_patch_non_negative CHECK (game_patch IS NULL OR game_patch >= 0),
    CONSTRAINT chk_selection_suffix_requires_patch CHECK (
        game_patch_suffix IS NULL OR (game_patch IS NOT NULL AND BTRIM(game_patch_suffix) <> '')
    )
);

INSERT INTO poe_companion.account_evaluation_selection_by_user (
    application_user_id, account_context_id, compatibility_version_major,
    compatibility_version_minor, game_patch, game_patch_suffix
)
SELECT '00000000-0000-4000-8000-000000006001'::uuid,
       account_context_id, compatibility_version_major,
       compatibility_version_minor, game_patch, game_patch_suffix
FROM poe_companion.account_evaluation_selection;

DROP TABLE poe_companion.account_evaluation_selection;
ALTER TABLE poe_companion.account_evaluation_selection_by_user RENAME TO account_evaluation_selection;
