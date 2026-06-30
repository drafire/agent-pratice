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