-- Eine gemeinsame Tabelle für Grundgericht und Variante, analog zu ingredient.
-- Genau ein Ziel, getrennte Eindeutigkeit je Ziel. Löschen von Gericht, Variante oder Benutzer entfernt die Zeilen.

CREATE TABLE rating (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    dish_id uuid REFERENCES dish (id) ON DELETE CASCADE,
    variant_id uuid REFERENCES dish_variant (id) ON DELETE CASCADE,
    stars smallint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT rating_stars_range CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT rating_one_target CHECK (
        (dish_id IS NOT NULL AND variant_id IS NULL)
        OR (dish_id IS NULL AND variant_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX rating_user_dish_unique ON rating (user_id, dish_id) WHERE dish_id IS NOT NULL;
CREATE UNIQUE INDEX rating_user_variant_unique ON rating (user_id, variant_id) WHERE variant_id IS NOT NULL;
CREATE INDEX rating_user_id_idx ON rating (user_id);
CREATE INDEX rating_dish_id_idx ON rating (dish_id);
CREATE INDEX rating_variant_id_idx ON rating (variant_id);
