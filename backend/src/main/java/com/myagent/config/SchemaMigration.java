package com.myagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS users (" +
            "  id VARCHAR(36) PRIMARY KEY," +
            "  username VARCHAR(50) NOT NULL UNIQUE," +
            "  password_hash VARCHAR(255) NOT NULL," +
            "  created_at DATETIME NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        addColumnIfMissing("conversations", "user_id",
                "ALTER TABLE conversations ADD COLUMN user_id VARCHAR(36) NULL DEFAULT NULL");
        addIndexIfMissing("conversations", "idx_conversations_user_id",
                "CREATE INDEX idx_conversations_user_id ON conversations(user_id)");
        addColumnIfMissing("conversations", "system_prompt",
                "ALTER TABLE conversations ADD COLUMN system_prompt TEXT NULL DEFAULT NULL");
        addColumnIfMissing("messages", "reasoning",
                "ALTER TABLE messages ADD COLUMN reasoning TEXT NULL DEFAULT NULL");
        addColumnIfMissing("conversations", "share_token",
                "ALTER TABLE conversations ADD COLUMN share_token VARCHAR(64) NULL DEFAULT NULL");
        addColumnIfMissing("messages", "prompt_tokens",
                "ALTER TABLE messages ADD COLUMN prompt_tokens INT NULL DEFAULT NULL");
        addColumnIfMissing("messages", "completion_tokens",
                "ALTER TABLE messages ADD COLUMN completion_tokens INT NULL DEFAULT NULL");
        addColumnIfMissing("messages", "total_tokens",
                "ALTER TABLE messages ADD COLUMN total_tokens INT NULL DEFAULT NULL");
        addColumnIfMissing("conversations", "pinned",
                "ALTER TABLE conversations ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("conversations", "folder_name",
                "ALTER TABLE conversations ADD COLUMN folder_name VARCHAR(100) NULL DEFAULT NULL");
        addColumnIfMissing("messages", "starred",
                "ALTER TABLE messages ADD COLUMN starred BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("messages", "rating",
                "ALTER TABLE messages ADD COLUMN rating TINYINT NULL DEFAULT NULL");
        addColumnIfMissing("messages", "interrupted",
                "ALTER TABLE messages ADD COLUMN interrupted BOOLEAN NOT NULL DEFAULT FALSE");
        addIndexIfMissing("messages", "idx_messages_conv_time",
                "CREATE INDEX idx_messages_conv_time ON messages(conversation_id, created_at)");
        addColumnIfMissing("knowledge_documents", "file_hash",
                "ALTER TABLE knowledge_documents ADD COLUMN file_hash VARCHAR(64) NULL DEFAULT NULL");
        addColumnIfMissing("knowledge_documents", "updated_at",
                "ALTER TABLE knowledge_documents ADD COLUMN updated_at DATETIME NULL DEFAULT NULL");

        // Video generation tasks table
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS video_gen_tasks (" +
            "  id VARCHAR(36) PRIMARY KEY," +
            "  user_id VARCHAR(36) NOT NULL," +
            "  conversation_id VARCHAR(36)," +
            "  prompt TEXT NOT NULL," +
            "  req_key VARCHAR(64) NOT NULL DEFAULT 'jimeng_ti2v_v30_pro'," +
            "  duration INT DEFAULT 5," +
            "  aspect_ratio VARCHAR(8) DEFAULT '16:9'," +
            "  seed INT DEFAULT -1," +
            "  first_frame_url TEXT," +
            "  task_id VARCHAR(128)," +
            "  status VARCHAR(32) NOT NULL DEFAULT 'PENDING'," +
            "  video_path VARCHAR(512)," +
            "  original_video_url TEXT," +
            "  error_message TEXT," +
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  INDEX idx_vgt_user_id (user_id)," +
            "  INDEX idx_vgt_status (status)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
    }

    private void addColumnIfMissing(String table, String column, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class, table, column);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
            log.info("Schema migration: added column {}.{}", table, column);
        }
    }

    private void addIndexIfMissing(String table, String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
            log.info("Schema migration: created index {} on {}", indexName, table);
        }
    }
}
