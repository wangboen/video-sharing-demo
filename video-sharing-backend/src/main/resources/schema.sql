CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE staff_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_staff_accounts_username UNIQUE (username),
    CONSTRAINT uk_staff_accounts_email UNIQUE (email),
    CONSTRAINT chk_staff_accounts_role CHECK (role IN ('REVIEWER', 'ADMIN'))
);

CREATE TABLE videos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    file_path VARCHAR(255) NOT NULL,
    thumbnail_path VARCHAR(255) NULL,
    upload_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    views BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    rejection_reason VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    lease_owner_id BIGINT NULL,
    claimed_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    CONSTRAINT chk_videos_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_videos_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_videos_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES staff_accounts(id) ON DELETE SET NULL,
    CONSTRAINT fk_videos_lease_owner
        FOREIGN KEY (lease_owner_id) REFERENCES staff_accounts(id) ON DELETE SET NULL
);

CREATE INDEX idx_staff_accounts_role_username
    ON staff_accounts (role, username);

CREATE INDEX idx_videos_status_upload_id
    ON videos (status, upload_date, id);

CREATE INDEX idx_videos_lease_owner_status_expiry
    ON videos (lease_owner_id, status, lease_expires_at);

CREATE INDEX idx_videos_user_upload_id
    ON videos (user_id, upload_date, id);

CREATE INDEX idx_videos_reviewer_id
    ON videos (reviewer_id);
