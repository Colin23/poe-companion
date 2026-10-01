CREATE TABLE poe_companion.account_evaluation_selection (
    singleton_id INTEGER NOT NULL,
    account_context_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    game_patch INTEGER,
    game_patch_suffix VARCHAR(32),
    CONSTRAINT pk_account_evaluation_selection PRIMARY KEY (singleton_id),
    CONSTRAINT chk_account_evaluation_selection_singleton CHECK (singleton_id = 1),
    CONSTRAINT fk_account_evaluation_selection_account_context
        FOREIGN KEY (account_context_id)
        REFERENCES poe_companion.account_context (id),
    CONSTRAINT chk_account_evaluation_selection_major_non_negative CHECK (compatibility_version_major >= 0),
    CONSTRAINT chk_account_evaluation_selection_minor_non_negative CHECK (compatibility_version_minor >= 0),
    CONSTRAINT chk_account_evaluation_selection_patch_non_negative CHECK (game_patch IS NULL OR game_patch >= 0),
    CONSTRAINT chk_account_evaluation_selection_suffix_requires_patch
        CHECK (
            game_patch_suffix IS NULL
            OR (
                game_patch IS NOT NULL
                AND BTRIM(game_patch_suffix) <> ''
            )
        )
);
