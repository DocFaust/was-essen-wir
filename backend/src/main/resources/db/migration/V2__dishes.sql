CREATE TABLE dish (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    description text,
    effort varchar(20),
    recipe_text text,
    recipe_url varchar(2000),
    recipe_source varchar(500),
    comments text,
    meal_prep boolean,
    homeoffice boolean,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT dish_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT dish_effort_known CHECK (effort IS NULL OR effort IN ('EINFACH', 'MITTEL', 'AUFWENDIG'))
);

CREATE INDEX dish_name_lower_idx ON dish (lower(name));

CREATE TABLE dish_meal_type (
    dish_id uuid NOT NULL REFERENCES dish (id) ON DELETE CASCADE,
    meal_type varchar(20) NOT NULL,
    PRIMARY KEY (dish_id, meal_type),
    CONSTRAINT dish_meal_type_known CHECK (meal_type IN ('FRUEHSTUECK', 'MITTAGESSEN', 'ABENDESSEN', 'SNACK'))
);

CREATE TABLE dish_category (
    dish_id uuid NOT NULL REFERENCES dish (id) ON DELETE CASCADE,
    position integer NOT NULL,
    name varchar(80) NOT NULL,
    PRIMARY KEY (dish_id, position),
    CONSTRAINT dish_category_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE UNIQUE INDEX dish_category_name_unique ON dish_category (dish_id, lower(name));

CREATE TABLE dish_variant (
    id uuid PRIMARY KEY,
    dish_id uuid NOT NULL REFERENCES dish (id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    description text,
    effort varchar(20),
    ingredients_specified boolean NOT NULL,
    recipe_text text,
    recipe_url varchar(2000),
    recipe_source varchar(500),
    meal_prep boolean,
    homeoffice boolean,
    position integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT dish_variant_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT dish_variant_effort_known CHECK (effort IS NULL OR effort IN ('EINFACH', 'MITTEL', 'AUFWENDIG'))
);

CREATE UNIQUE INDEX dish_variant_name_unique ON dish_variant (dish_id, lower(name));
CREATE INDEX dish_variant_dish_id_idx ON dish_variant (dish_id);

CREATE TABLE ingredient (
    id uuid PRIMARY KEY,
    dish_id uuid REFERENCES dish (id) ON DELETE CASCADE,
    variant_id uuid REFERENCES dish_variant (id) ON DELETE CASCADE,
    position integer NOT NULL,
    name varchar(120) NOT NULL,
    CONSTRAINT ingredient_one_owner CHECK (
        (dish_id IS NOT NULL AND variant_id IS NULL)
        OR (dish_id IS NULL AND variant_id IS NOT NULL)
    ),
    CONSTRAINT ingredient_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE INDEX ingredient_dish_id_idx ON ingredient (dish_id);
CREATE INDEX ingredient_variant_id_idx ON ingredient (variant_id);
