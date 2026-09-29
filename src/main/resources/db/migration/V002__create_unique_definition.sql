-- A UniqueDefinition can exist before metadata such as names or provider mappings is known.
CREATE TABLE poe_companion.unique_definition (
    id UUID NOT NULL,
    CONSTRAINT pk_unique_definition PRIMARY KEY (id)
);
