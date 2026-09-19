DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id            VARCHAR(64)  NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    age           INT,
    email         VARCHAR(128),
    cell_phone    VARCHAR(32),
    id_card_no    VARCHAR(32),
    address       VARCHAR(255),
    province      VARCHAR(64),
    license_plate VARCHAR(32),
    create_time   TIMESTAMP,
    update_time   TIMESTAMP,
    PRIMARY KEY (id)
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

DROP TABLE IF EXISTS t_auto_user;

CREATE TABLE t_auto_user (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    username VARCHAR(64)  NOT NULL,
    email    VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE KEY uk_auto_username (username)
);
