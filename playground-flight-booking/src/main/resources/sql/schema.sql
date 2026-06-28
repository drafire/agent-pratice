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