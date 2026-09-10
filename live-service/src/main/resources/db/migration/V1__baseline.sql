CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sys_user_username (username)
);

CREATE TABLE IF NOT EXISTS user_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    anchor_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_follow (user_id, anchor_id),
    KEY idx_follow_anchor (anchor_id)
);

CREATE TABLE IF NOT EXISTS live_room (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    anchor_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    cover_url VARCHAR(512) NULL,
    category VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
    stream_key_hash VARCHAR(128) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_live_room_anchor (anchor_id),
    KEY idx_live_room_status_category (status, category)
);

CREATE TABLE IF NOT EXISTS live_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    started_at TIMESTAMP(3) NOT NULL,
    ended_at TIMESTAMP(3) NULL,
    playback_url VARCHAR(1024) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_live_session_room_started (room_id, started_at)
);
