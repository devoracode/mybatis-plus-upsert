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

-- 序列主键表：主键由数据库序列生成（对应 PostgreSQL/Oracle 的 identity/sequence 策略），
-- 序列号不会随事务回滚，因此测试只断言取值来自序列且互不相同，不断言具体数字。
DROP SEQUENCE IF EXISTS seq_key_seq_user;
CREATE SEQUENCE seq_key_seq_user START WITH 1000 INCREMENT BY 1;

DROP TABLE IF EXISTS t_key_seq_user;

CREATE TABLE t_key_seq_user (
    id       BIGINT       NOT NULL,
    username VARCHAR(64)  NOT NULL,
    email    VARCHAR(128),
    PRIMARY KEY (id),
    UNIQUE KEY uk_seq_username (username)
);

-- 完全没有主键的表：冲突键即业务唯一键，验证缺少主键不会让 Upsert 报错。
DROP TABLE IF EXISTS t_keyless_user;

CREATE TABLE t_keyless_user (
    username VARCHAR(64)  NOT NULL,
    email    VARCHAR(128),
    PRIMARY KEY (username)
);
