CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    file_path VARCHAR(255) NOT NULL,
    thumbnail_path VARCHAR(255),
    upload_date DATETIME DEFAULT CURRENT_TIMESTAMP,
    views BIGINT DEFAULT 0,
    status VARCHAR(20) NULL,
    reviewer_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    rejection_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    lease_owner_id BIGINT NULL,
    claimed_at DATETIME NULL,
    lease_expires_at DATETIME NULL,
    FOREIGN KEY (reviewer_id) REFERENCES users(id),
    FOREIGN KEY (lease_owner_id) REFERENCES users(id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
