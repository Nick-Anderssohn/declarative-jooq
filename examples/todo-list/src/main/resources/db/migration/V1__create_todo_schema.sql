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

CREATE TABLE label (
    todo_list_id BIGINT NOT NULL REFERENCES todo_list(id) ON DELETE CASCADE,
    name         VARCHAR(100) NOT NULL,
    color        VARCHAR(7) DEFAULT '#3b82f6',
    PRIMARY KEY (todo_list_id, name)
);

CREATE TABLE todo_item_label (
    id           BIGSERIAL PRIMARY KEY,
    todo_item_id BIGINT NOT NULL REFERENCES todo_item(id) ON DELETE CASCADE,
    todo_list_id BIGINT NOT NULL,
    label_name   VARCHAR(100) NOT NULL,
    FOREIGN KEY (todo_list_id, label_name) REFERENCES label(todo_list_id, name) ON DELETE CASCADE
);
