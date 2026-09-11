CREATE TABLE IF NOT EXISTS auth_refresh_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_auth_refresh_token_hash (token_hash),
    KEY idx_auth_refresh_user (user_id),
    KEY idx_auth_refresh_expire (expires_at)
);

CREATE TABLE IF NOT EXISTS sensitive_word (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    word VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sensitive_word_word (word)
);

CREATE TABLE IF NOT EXISTS content_moderation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(512) NOT NULL,
    matched_word VARCHAR(128) NOT NULL,
    action VARCHAR(32) NOT NULL DEFAULT 'BLOCKED',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_moderation_room_created (room_id, created_at),
    KEY idx_moderation_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS room_mute (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    reason VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP(3) NULL,
    revoked_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_room_mute_lookup (room_id, user_id, revoked_at, expires_at),
    KEY idx_room_mute_created (created_at)
);

CREATE TABLE IF NOT EXISTS ops_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    reason VARCHAR(512) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_ops_audit_created (created_at),
    KEY idx_ops_audit_operator (operator_id)
);
