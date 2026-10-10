CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    username varchar(40) NOT NULL,
    username_normalized varchar(40) NOT NULL,
    display_name varchar(80) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role varchar(16) NOT NULL,
    active boolean NOT NULL,
    password_change_required boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT app_user_username_not_blank CHECK (length(btrim(username)) > 0),
    CONSTRAINT app_user_display_name_not_blank CHECK (length(btrim(display_name)) > 0),
    CONSTRAINT app_user_role_known CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT app_user_password_hash_not_blank CHECK (length(password_hash) > 0)
);

CREATE UNIQUE INDEX app_user_username_normalized_unique ON app_user (username_normalized);

CREATE TABLE app_setup (
    id smallint PRIMARY KEY,
    token_hash bytea,
    expires_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT app_setup_singleton CHECK (id = 1),
    CONSTRAINT app_setup_open_token CHECK (
        completed_at IS NOT NULL
        OR (token_hash IS NOT NULL AND expires_at IS NOT NULL)
    )
);
