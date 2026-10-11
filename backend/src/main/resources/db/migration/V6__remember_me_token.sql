-- Dauerhafte Anmeldung pro Browser. Der Selektor ist nur der Suchschlüssel.
-- Gespeichert wird SHA-256 des geheimen Anteils, nie der Klartext.

CREATE TABLE remember_me_token (
    selector varchar(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash bytea NOT NULL,
    previous_token_hash bytea,
    rotated_at timestamptz,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT remember_me_token_selector_not_blank CHECK (length(btrim(selector)) > 0),
    CONSTRAINT remember_me_token_hash_length CHECK (octet_length(token_hash) = 32),
    CONSTRAINT remember_me_previous_hash_length CHECK (
        previous_token_hash IS NULL OR octet_length(previous_token_hash) = 32
    )
);

CREATE INDEX remember_me_token_user_created_idx ON remember_me_token (user_id, created_at DESC);
CREATE INDEX remember_me_token_expires_at_idx ON remember_me_token (expires_at);
