CREATE TABLE hospital (
    id INT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    address VARCHAR(300) NOT NULL,
    description VARCHAR(2000) NOT NULL
);
INSERT INTO hospital (id, name, address, description) VALUES (1, '校医院', '', '');
CREATE TABLE department (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL
);
