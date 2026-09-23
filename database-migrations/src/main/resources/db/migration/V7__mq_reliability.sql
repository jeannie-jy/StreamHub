CREATE TABLE IF NOT EXISTS mq_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(191) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    message_tag VARCHAR(64) NULL,
    message_key VARCHAR(128) NULL,
    body MEDIUMTEXT NOT NULL,
    delay_level INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    sent_at TIMESTAMP(3) NULL,
    UNIQUE KEY uk_mq_outbox_event_key (event_key),
    KEY idx_mq_outbox_dispatch (status, next_attempt_at, id)
);

CREATE TABLE IF NOT EXISTS mq_dead_letter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    consumer_group VARCHAR(255) NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    message_id VARCHAR(191) NOT NULL,
    message_keys VARCHAR(255) NULL,
    message_tag VARCHAR(64) NULL,
    body MEDIUMTEXT NOT NULL,
    reconsume_times INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1000) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    replay_outbox_id BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    handled_at TIMESTAMP(3) NULL,
    UNIQUE KEY uk_mq_dead_letter_message (consumer_group, message_id),
    KEY idx_mq_dead_letter_status_created (status, created_at)
);
