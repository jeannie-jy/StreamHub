CREATE TABLE IF NOT EXISTS live_room_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_live_room_favorite_user_room (user_id, room_id),
    KEY idx_live_room_favorite_user_created (user_id, created_at),
    KEY idx_live_room_favorite_room (room_id)
);
