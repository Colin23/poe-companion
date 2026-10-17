-- Boss identity is application-owned. Display names are metadata, not identity or unique keys.
CREATE TABLE poe_companion.boss_definition (
    id UUID NOT NULL,
    display_name TEXT NOT NULL,
    CONSTRAINT pk_boss_definition PRIMARY KEY (id),
    CONSTRAINT chk_boss_definition_display_name_nonblank CHECK (btrim(display_name) <> '')
);
