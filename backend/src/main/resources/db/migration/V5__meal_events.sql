-- Eine Zeile ist entweder geplant oder gegessen. Bestätigen ändert den Status
-- und legt keinen zweiten Eintrag an. Namen bleiben als Snapshot stehen, wenn
-- Gericht oder Variante später gelöscht werden. Benutzerverweise werden dann
-- geleert, der Anzeigename der Mitessenden bleibt.

CREATE TABLE meal_event (
    id uuid PRIMARY KEY,
    meal_date date NOT NULL,
    meal_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    dish_id uuid REFERENCES dish (id) ON DELETE SET NULL,
    variant_id uuid REFERENCES dish_variant (id) ON DELETE SET NULL,
    dish_name varchar(120) NOT NULL,
    variant_name varchar(120),
    comment varchar(500),
    created_by uuid REFERENCES app_user (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT meal_event_status_known CHECK (status IN ('PLANNED', 'EATEN')),
    CONSTRAINT meal_event_meal_type_known CHECK (meal_type IN ('FRUEHSTUECK', 'MITTAGESSEN', 'ABENDESSEN', 'SNACK')),
    CONSTRAINT meal_event_dish_name_not_blank CHECK (length(btrim(dish_name)) > 0),
    CONSTRAINT meal_event_variant_name_not_blank CHECK (variant_name IS NULL OR length(btrim(variant_name)) > 0)
);

CREATE INDEX meal_event_date_idx ON meal_event (meal_date);
CREATE INDEX meal_event_status_date_idx ON meal_event (status, meal_date);
CREATE INDEX meal_event_dish_id_idx ON meal_event (dish_id);

CREATE TABLE meal_participant (
    id uuid PRIMARY KEY,
    meal_event_id uuid NOT NULL REFERENCES meal_event (id) ON DELETE CASCADE,
    user_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
    display_name varchar(80) NOT NULL,
    CONSTRAINT meal_participant_name_not_blank CHECK (length(btrim(display_name)) > 0)
);

CREATE UNIQUE INDEX meal_participant_user_unique
    ON meal_participant (meal_event_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE INDEX meal_participant_event_idx ON meal_participant (meal_event_id);
CREATE INDEX meal_participant_user_idx ON meal_participant (user_id);

-- Prüft am Transaktionsende die aktuelle Zeile. Fremdschlüssel dürfen Gericht
-- und Variante beim Löschen zwischenzeitlich leeren, ohne die Historie zu verwerfen.
CREATE OR REPLACE FUNCTION meal_event_variant_matches_dish()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    current_dish uuid;
    current_variant uuid;
BEGIN
    SELECT dish_id, variant_id INTO current_dish, current_variant
    FROM meal_event
    WHERE id = NEW.id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;
    IF current_variant IS NOT NULL AND current_dish IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM dish_variant
        WHERE id = current_variant
          AND dish_id = current_dish
    ) THEN
        RAISE EXCEPTION 'variant does not belong to dish'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER meal_event_variant_matches_dish
AFTER INSERT OR UPDATE ON meal_event
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION meal_event_variant_matches_dish();

CREATE OR REPLACE FUNCTION meal_event_requires_participant()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    event_id uuid;
BEGIN
    IF TG_TABLE_NAME = 'meal_event' THEN
        event_id := NEW.id;
    ELSE
        event_id := COALESCE(NEW.meal_event_id, OLD.meal_event_id);
    END IF;
    IF EXISTS (SELECT 1 FROM meal_event WHERE id = event_id)
       AND NOT EXISTS (SELECT 1 FROM meal_participant WHERE meal_event_id = event_id) THEN
        RAISE EXCEPTION 'meal requires participant'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER meal_event_requires_participant
AFTER INSERT OR UPDATE ON meal_event
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION meal_event_requires_participant();

CREATE CONSTRAINT TRIGGER meal_participant_requires_participant
AFTER DELETE OR UPDATE ON meal_participant
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION meal_event_requires_participant();
