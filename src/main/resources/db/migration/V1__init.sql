CREATE TABLE IF NOT EXISTS users (
    id       BIGSERIAL    PRIMARY KEY,
    username VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,                                              -- BCrypt hash, never plain text
    role     VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'OPERATOR', 'GUEST'))
);

CREATE TABLE IF NOT EXISTS bookmarks (
    title       VARCHAR(255)  NOT NULL,
    description VARCHAR(2000) NOT NULL,
    tag         VARCHAR(100),
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (title, user_id)             -- composite key: same title allowed across different users
);
