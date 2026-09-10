CREATE TABLE IF NOT EXISTS wallet_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_wallet_account_user (user_id)
);

CREATE TABLE IF NOT EXISTS wallet_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_no VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    change_amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    reference_id VARCHAR(128) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_wallet_ledger_biz_no (biz_no),
    KEY idx_wallet_ledger_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS gift_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    price BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_gift_catalog_code (code)
);

CREATE TABLE IF NOT EXISTS gift_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(128) NOT NULL,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    anchor_id BIGINT NOT NULL,
    gift_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    total_amount BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(255) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at TIMESTAMP(3) NULL,
    UNIQUE KEY uk_gift_order_no (order_no),
    KEY idx_gift_order_room_created (room_id, created_at),
    KEY idx_gift_order_sender_created (sender_id, created_at)
);

INSERT IGNORE INTO gift_catalog(code, name, price, status) VALUES
    ('rose', '玫瑰', 1, 'ACTIVE'),
    ('rocket', '火箭', 100, 'ACTIVE'),
    ('crown', '皇冠', 1000, 'ACTIVE');
