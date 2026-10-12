-- Canonical relationship identity is independent of versioned CAN_DROP/CANNOT_DROP knowledge.
CREATE TABLE poe_companion.drop_relationship (
    id UUID NOT NULL,
    acquisition_source_id UUID NOT NULL,
    unique_definition_id UUID NOT NULL,
    CONSTRAINT pk_drop_relationship PRIMARY KEY (id),
    CONSTRAINT uq_drop_relationship_source_unique UNIQUE (acquisition_source_id, unique_definition_id),
    CONSTRAINT fk_drop_relationship_source
        FOREIGN KEY (acquisition_source_id)
        REFERENCES poe_companion.acquisition_source (id),
    CONSTRAINT fk_drop_relationship_unique
        FOREIGN KEY (unique_definition_id)
        REFERENCES poe_companion.unique_definition (id)
);
