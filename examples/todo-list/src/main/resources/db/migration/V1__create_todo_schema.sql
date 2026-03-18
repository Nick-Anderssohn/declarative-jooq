CREATE TABLE app_user (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE todo_list (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    created_by  BIGINT REFERENCES app_user(id),
    updated_by  BIGINT REFERENCES app_user(id),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE todo_item (
    id           BIGSERIAL PRIMARY KEY,
    todo_list_id BIGINT NOT NULL REFERENCES todo_list(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    completed    BOOLEAN DEFAULT FALSE,
    created_by   BIGINT REFERENCES app_user(id),
    updated_by   BIGINT REFERENCES app_user(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE shared_with (
    id           BIGSERIAL PRIMARY KEY,
    todo_list_id BIGINT NOT NULL REFERENCES todo_list(id) ON DELETE CASCADE,
    user_id      BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    UNIQUE(todo_list_id, user_id)
);
