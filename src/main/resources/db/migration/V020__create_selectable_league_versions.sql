-- A recognized league/version pairing is not a league lifecycle assertion.
CREATE TABLE poe_companion.selectable_league_version (
    league_definition_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,

    CONSTRAINT pk_selectable_league_version
        PRIMARY KEY (league_definition_id, compatibility_version_major, compatibility_version_minor),

    CONSTRAINT fk_selectable_league_version_league
        FOREIGN KEY (league_definition_id)
        REFERENCES poe_companion.league_definition (id),

    CONSTRAINT chk_selectable_league_version_compatibility
        CHECK (compatibility_version_major >= 0 AND compatibility_version_minor >= 0)
);
