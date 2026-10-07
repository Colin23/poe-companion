CREATE TABLE poe_companion.build_archetype (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_build_archetype PRIMARY KEY (id)
);

CREATE TABLE poe_companion.build_variant (
    id UUID NOT NULL,
    build_archetype_id UUID NOT NULL,
    label VARCHAR(255),
    CONSTRAINT pk_build_variant PRIMARY KEY (id),
    CONSTRAINT fk_build_variant_build_archetype
        FOREIGN KEY (build_archetype_id)
        REFERENCES poe_companion.build_archetype (id)
);

CREATE TABLE poe_companion.build_variant_revision (
    id UUID NOT NULL,
    build_variant_id UUID NOT NULL,
    compatibility_version_major INTEGER NOT NULL,
    compatibility_version_minor INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT pk_build_variant_revision PRIMARY KEY (id),
    CONSTRAINT fk_build_variant_revision_build_variant
        FOREIGN KEY (build_variant_id)
        REFERENCES poe_companion.build_variant (id),
    CONSTRAINT chk_build_variant_revision_major_non_negative
        CHECK (compatibility_version_major >= 0),
    CONSTRAINT chk_build_variant_revision_minor_non_negative
        CHECK (compatibility_version_minor >= 0),
    CONSTRAINT chk_build_variant_revision_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'SUPERSEDED'))
);

CREATE UNIQUE INDEX uq_build_variant_revision_active_version
    ON poe_companion.build_variant_revision (
        build_variant_id,
        compatibility_version_major,
        compatibility_version_minor
    )
    WHERE status = 'ACTIVE';

CREATE TABLE poe_companion.build_requirement_group (
    build_variant_revision_id UUID NOT NULL,
    position INTEGER NOT NULL,
    importance VARCHAR(32) NOT NULL,
    logic VARCHAR(32) NOT NULL,
    CONSTRAINT pk_build_requirement_group
        PRIMARY KEY (build_variant_revision_id, position),
    CONSTRAINT fk_build_requirement_group_revision
        FOREIGN KEY (build_variant_revision_id)
        REFERENCES poe_companion.build_variant_revision (id),
    CONSTRAINT chk_build_requirement_group_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT chk_build_requirement_group_importance
        CHECK (importance IN ('ENABLING', 'CORE', 'UPGRADE')),
    CONSTRAINT chk_build_requirement_group_logic
        CHECK (logic IN ('ALL', 'ANY'))
);

CREATE TABLE poe_companion.build_requirement (
    build_variant_revision_id UUID NOT NULL,
    group_position INTEGER NOT NULL,
    position INTEGER NOT NULL,
    unique_definition_id UUID NOT NULL,
    required_quantity INTEGER NOT NULL,
    CONSTRAINT pk_build_requirement
        PRIMARY KEY (build_variant_revision_id, group_position, position),
    CONSTRAINT fk_build_requirement_group
        FOREIGN KEY (build_variant_revision_id, group_position)
        REFERENCES poe_companion.build_requirement_group (build_variant_revision_id, position),
    CONSTRAINT fk_build_requirement_unique_definition
        FOREIGN KEY (unique_definition_id)
        REFERENCES poe_companion.unique_definition (id),
    CONSTRAINT uq_build_requirement_unique_in_group
        UNIQUE (build_variant_revision_id, group_position, unique_definition_id),
    CONSTRAINT chk_build_requirement_position_non_negative
        CHECK (position >= 0),
    CONSTRAINT chk_build_requirement_quantity_positive
        CHECK (required_quantity > 0)
);
