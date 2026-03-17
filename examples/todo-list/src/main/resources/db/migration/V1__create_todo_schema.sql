CREATE TABLE todo_list (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE todo_item (
    id           BIGSERIAL PRIMARY KEY,
    todo_list_id BIGINT NOT NULL REFERENCES todo_list(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    completed    BOOLEAN DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
