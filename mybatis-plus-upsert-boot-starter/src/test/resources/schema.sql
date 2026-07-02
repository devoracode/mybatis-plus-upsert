DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id          BIGINT       NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    email       VARCHAR(128),
    age         INT,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);

DROP TABLE IF EXISTS t_secret_holder;

CREATE TABLE t_secret_holder (
    id          BIGINT       NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    secret      VARCHAR(128) DEFAULT 'DB_DEFAULT',
    visible     VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
);
