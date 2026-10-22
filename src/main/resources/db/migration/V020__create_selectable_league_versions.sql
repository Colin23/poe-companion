-- Recognized league/version selections; no lifecycle inference.
CREATE TABLE poe_companion.selectable_league_version (
    league_definition_id UUID NOT NULL REFERENCES poe_companion.league_definition(id),
    compatibility_version_major INTEGER NOT NULL CHECK (compatibility_version_major >= 0),
    compatibility_version_minor INTEGER NOT NULL CHECK (compatibility_version_minor >= 0),
    PRIMARY KEY (league_definition_id, compatibility_version_major, compatibility_version_minor)
);
