CREATE TABLE doctor_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doctor_id VARCHAR(100),
    email VARCHAR(255) UNIQUE,
    access_token TEXT,
    refresh_token TEXT,
    token_expiry BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE appointment_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doctor_email VARCHAR(255),
    patient_email VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    slot_start DATETIME,
    slot_end DATETIME,
    event_id VARCHAR(255),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);