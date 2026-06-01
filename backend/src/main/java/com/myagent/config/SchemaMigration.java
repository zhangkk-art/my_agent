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
}
