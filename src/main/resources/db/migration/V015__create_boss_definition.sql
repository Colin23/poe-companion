-- Boss identity is application-owned. Display names are metadata, not identity or unique keys.
CREATE TABLE poe_companion.boss_definition (
    id UUID NOT NULL,
    display_name TEXT NOT NULL,
    CONSTRAINT pk_boss_definition PRIMARY KEY (id),
    -- Require at least one non-whitespace character. POSIX space covers tabs/newlines;
    -- C collation fixes range/class semantics; explicit Unicode/control ranges cover Kotlin Char.isWhitespace.
    CONSTRAINT chk_boss_definition_display_name_nonblank
        CHECK (display_name COLLATE "C" ~ '[^[:space:]\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\u001C-\u001F]')
);
