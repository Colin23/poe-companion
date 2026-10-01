CREATE TABLE poe_companion.league_definition (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    league_category VARCHAR(32) NOT NULL,
    participation VARCHAR(32) NOT NULL,
    mortality VARCHAR(32) NOT NULL,
    realm VARCHAR(32) NOT NULL,
    CONSTRAINT pk_league_definition PRIMARY KEY (id),
    CONSTRAINT chk_league_definition_category CHECK (league_category IN ('STANDARD', 'CHALLENGE')),
    CONSTRAINT chk_league_definition_participation CHECK (participation IN ('SSF', 'TRADE')),
    CONSTRAINT chk_league_definition_mortality CHECK (mortality IN ('SOFTCORE', 'HARDCORE')),
    CONSTRAINT chk_league_definition_realm CHECK (realm IN ('PC', 'XBOX', 'SONY'))
);

CREATE TABLE poe_companion.account_context (
    id UUID NOT NULL,
    league_definition_id UUID NOT NULL,
    ruleset VARCHAR(32) NOT NULL,
    CONSTRAINT pk_account_context PRIMARY KEY (id),
    CONSTRAINT fk_account_context_league_definition
        FOREIGN KEY (league_definition_id)
        REFERENCES poe_companion.league_definition (id),
    CONSTRAINT chk_account_context_ruleset CHECK (ruleset IN ('NORMAL', 'RUTHLESS'))
);
