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

        // Migrate old frames column to duration if needed
        Integer framesCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'video_gen_tasks' AND column_name = 'frames'",
                Integer.class);
        if (framesCount != null && framesCount > 0) {
            jdbcTemplate.execute("ALTER TABLE video_gen_tasks CHANGE COLUMN frames duration INT DEFAULT 5");
            log.info("Schema migration: renamed frames to duration in video_gen_tasks");
        }

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

        // Subtitle support for video generation
        addColumnIfMissing("video_gen_tasks", "subtitle_enabled",
                "ALTER TABLE video_gen_tasks ADD COLUMN subtitle_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("video_gen_tasks", "subtitle_path",
                "ALTER TABLE video_gen_tasks ADD COLUMN subtitle_path VARCHAR(512) NULL DEFAULT NULL");
        addColumnIfMissing("video_gen_tasks", "generate_audio",
                "ALTER TABLE video_gen_tasks ADD COLUMN generate_audio BOOLEAN NOT NULL DEFAULT TRUE");
        addColumnIfMissing("video_gen_tasks", "narrate_subtitles",
                "ALTER TABLE video_gen_tasks ADD COLUMN narrate_subtitles BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfMissing("video_gen_tasks", "custom_subtitles",
                "ALTER TABLE video_gen_tasks ADD COLUMN custom_subtitles TEXT NULL DEFAULT NULL");
        addColumnIfMissing("video_gen_tasks", "negative_prompt",
                "ALTER TABLE video_gen_tasks ADD COLUMN negative_prompt TEXT NULL DEFAULT NULL");

        // ── Video prompt templates table ──
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS video_prompt_templates (" +
            "  id VARCHAR(36) PRIMARY KEY," +
            "  name VARCHAR(100) NOT NULL," +
            "  content TEXT NOT NULL," +
            "  category VARCHAR(50) DEFAULT 'general'," +
            "  sort_order INT DEFAULT 0," +
            "  is_preset BOOLEAN NOT NULL DEFAULT FALSE," +
            "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        // Seed preset templates only if table is empty
        Integer templateCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_prompt_templates", Integer.class);
        if (templateCount != null && templateCount == 0) {
            String[][] presets = {
                {"电影大片", "cinematic",
                 "Cinematic masterpiece, shallow depth of field, golden hour lighting, 8K quality, anamorphic lens, film grain texture, dramatic color grading, slow motion camera movement, epic composition"},
                {"日系动漫", "anime",
                 "Studio Ghibli inspired animation style, vibrant cel shading, soft pastel color palette, gentle breeze animating hair and clothing, dreamlike atmosphere, cherry blossom petals floating, warm sunlight streaming through leaves"},
                {"商业广告", "commercial",
                 "Professional product commercial shot, studio lighting setup, clean minimalist background, 60fps smooth slow motion, macro lens capturing fine details, elegant presentation, premium quality feel"},
                {"写实纪录片", "realistic",
                 "Photorealistic documentary style, natural ambient lighting, handheld camera with subtle movement, shallow depth of field on subject, detailed textures and imperfections, authentic atmosphere"},
                {"国风美学", "chinese",
                 "中国古典风格，水墨意境，留白构图，丝绸飘动，烟雾缭绕，金色与朱红色调，意境深远，含蓄典雅，如诗如画"},
                {"赛博朋克", "cyberpunk",
                 "Cyberpunk cityscape at night, neon lights reflecting on wet streets, holographic advertisements flickering, volumetric fog rolling through alleyways, blue and magenta color palette, high contrast lighting, rain droplets"}
            };
            for (int i = 0; i < presets.length; i++) {
                jdbcTemplate.update(
                    "INSERT INTO video_prompt_templates (id, name, content, category, sort_order, is_preset, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, TRUE, NOW(), NOW())",
                    java.util.UUID.randomUUID().toString(),
                    presets[i][0], presets[i][2], presets[i][1], i);
            }
            log.info("Schema migration: seeded {} video prompt templates", presets.length);
        }
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
