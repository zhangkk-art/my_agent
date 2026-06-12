CREATE TABLE IF NOT EXISTS prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    system_prompt TEXT NULL DEFAULT NULL,
    share_token VARCHAR(64) NULL DEFAULT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS workflow_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL DEFAULT NULL,
    system_prompt TEXT NULL DEFAULT NULL,
    initial_message TEXT NULL DEFAULT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    reasoning TEXT NULL DEFAULT NULL,
    prompt_tokens INT NULL DEFAULT NULL,
    completion_tokens INT NULL DEFAULT NULL,
    total_tokens INT NULL DEFAULT NULL,
    created_at DATETIME NOT NULL,
        INDEX idx_msg_conv (conversation_id, created_at),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS video_gen_tasks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36),
    prompt TEXT NOT NULL,
    req_key VARCHAR(64) NOT NULL DEFAULT 'jimeng_ti2v_v30_pro',
    duration INT DEFAULT 5,
    aspect_ratio VARCHAR(8) DEFAULT '16:9',
    seed INT DEFAULT -1,
    first_frame_url TEXT,
    task_id VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    video_path VARCHAR(512),
    original_video_url TEXT,
    subtitle_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    subtitle_path VARCHAR(512) NULL DEFAULT NULL,
    negative_prompt TEXT NULL DEFAULT NULL,
    error_message TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_vgt_user_id (user_id),
    INDEX idx_vgt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS video_prompt_templates (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) DEFAULT 'general',
    sort_order INT DEFAULT 0,
    is_preset BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS storyboards (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    conversation_id VARCHAR(36),
    title VARCHAR(200) NOT NULL,
    idea TEXT NOT NULL,
    shot_count INT DEFAULT 5,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sb_user (user_id),
    INDEX idx_sb_conv (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS storyboard_shots (
    id VARCHAR(36) PRIMARY KEY,
    storyboard_id VARCHAR(36) NOT NULL,
    scene_number INT NOT NULL,
    scene_note VARCHAR(200),
    shot_description TEXT NOT NULL,
    camera_movement VARCHAR(50),
    duration INT DEFAULT 5,
    audio_hint VARCHAR(200),
    sort_order INT DEFAULT 0,
    status VARCHAR(32) DEFAULT 'PENDING',
    task_id VARCHAR(36),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ss_storyboard (storyboard_id),
    FOREIGN KEY (storyboard_id) REFERENCES storyboards(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
