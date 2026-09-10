CREATE TABLE IF NOT EXISTS activity (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    stock INT NOT NULL,
    unit_price BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    starts_at TIMESTAMP(3) NOT NULL,
    ends_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_activity_room_status (room_id, status),
    KEY idx_activity_status_time (status, starts_at, ends_at)
);

CREATE TABLE IF NOT EXISTS activity_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(128) NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at TIMESTAMP(3) NULL,
    closed_at TIMESTAMP(3) NULL,
    UNIQUE KEY uk_activity_order_no (order_no),
    UNIQUE KEY uk_activity_order_user (activity_id, user_id),
    KEY idx_activity_order_activity_created (activity_id, created_at)
);
