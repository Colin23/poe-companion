-- A canonical AcquisitionSource has stable identity independently of its versioned encounter knowledge.
CREATE TABLE poe_companion.acquisition_source (
    id UUID NOT NULL,
    CONSTRAINT pk_acquisition_source PRIMARY KEY (id)
);
