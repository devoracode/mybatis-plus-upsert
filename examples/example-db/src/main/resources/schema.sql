CREATE TABLE IF NOT EXISTS t_user (
    id varchar(42) NOT NULL PRIMARY KEY,
    name varchar(255) DEFAULT NULL,
    age smallint DEFAULT NULL,
    email varchar(255) DEFAULT NULL,
    cell_phone varchar(100) DEFAULT NULL,
    id_card_no varchar(255) DEFAULT NULL,
    address varchar(255) DEFAULT NULL,
    province varchar(255) DEFAULT NULL,
    license_plate varchar(255) DEFAULT NULL,
    create_time timestamp(6) NULL DEFAULT NULL,
    update_time timestamp(6) NULL DEFAULT NULL
);