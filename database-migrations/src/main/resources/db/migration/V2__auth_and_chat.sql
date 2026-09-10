CREATE TABLE IF NOT EXISTS auth_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_auth_session_token (token),
    KEY idx_auth_session_user (user_id),
    KEY idx_auth_session_expire (expires_at)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    client_message_id VARCHAR(64) NOT NULL,
    content VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_chat_room_client_message (room_id, client_message_id),
    KEY idx_chat_room_id (room_id, id)
);
