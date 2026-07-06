-- MySQL Schema
CREATE TABLE IF NOT EXISTS t_user (
    id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(255) DEFAULT NULL,
    age SMALLINT DEFAULT NULL,
    email VARCHAR(255) DEFAULT NULL,
    cell_phone VARCHAR(100) DEFAULT NULL,
    id_card_no VARCHAR(255) DEFAULT NULL,
    address VARCHAR(255) DEFAULT NULL,
    province VARCHAR(255) DEFAULT NULL,
    license_plate VARCHAR(255) DEFAULT NULL,
    create_time TIMESTAMP(6) NULL DEFAULT NULL,
    update_time TIMESTAMP(6) NULL DEFAULT NULL
);

-- PostgreSQL Schema (same table structure)
-- CREATE TABLE IF NOT EXISTS t_user (
--     id BIGINT NOT NULL PRIMARY KEY,
--     name VARCHAR(255) DEFAULT NULL,
--     age SMALLINT DEFAULT NULL,
--     email VARCHAR(255) DEFAULT NULL,
--     cell_phone VARCHAR(100) DEFAULT NULL,
--     id_card_no VARCHAR(255) DEFAULT NULL,
--     address VARCHAR(255) DEFAULT NULL,
--     province VARCHAR(255) DEFAULT NULL,
--     license_plate VARCHAR(255) DEFAULT NULL,
--     create_time TIMESTAMP(6) NULL DEFAULT NULL,
--     update_time TIMESTAMP(6) NULL DEFAULT NULL
-- );
