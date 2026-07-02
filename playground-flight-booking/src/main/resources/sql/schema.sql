CREATE TABLE IF NOT EXISTS booking (
    booking_number VARCHAR(50) PRIMARY KEY,
    date         DATE         NOT NULL,
    booking_to   DATE,
    customer_name VARCHAR(100) NOT NULL,
    origin       VARCHAR(100) NOT NULL,
    destination  VARCHAR(100) NOT NULL,
    booking_status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    booking_class VARCHAR(20) NOT NULL DEFAULT 'ECONOMY'
);

CREATE TABLE IF NOT EXISTS flights (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    flight_number     VARCHAR(20)  NOT NULL,
    airline           VARCHAR(50)  NOT NULL,
    departure_city    VARCHAR(50)  NOT NULL,
    arrival_city      VARCHAR(50)  NOT NULL,
    departure_airport VARCHAR(50)  NOT NULL,
    arrival_airport   VARCHAR(50)  NOT NULL,
    departure_time    TIME         NOT NULL,
    arrival_time      TIME         NOT NULL,
    duration          INT          NOT NULL,
    aircraft_type     VARCHAR(50)  NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    available_seats   INT          NOT NULL DEFAULT 150,
    flight_date       DATE         NOT NULL,
    UNIQUE KEY uk_flight_date_num (flight_number, flight_date)
);

CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    id              VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    content         TEXT,
    type            VARCHAR(50),
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50)  NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL
);