package com.myagent.service;

import com.myagent.config.ChatClientRegistry;
import com.myagent.mapper.VideoPromptTemplateMapper;
import com.myagent.model.VideoPromptTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class VideoPromptService {

    private static final Logger log = LoggerFactory.getLogger(VideoPromptService.class);

    private static final String DEFAULT_CATEGORY = "custom";
    private static final int DEFAULT_SORT_ORDER = 100;

    private final ChatClientRegistry clientRegistry;
    private final VideoPromptTemplateMapper templateMapper;

    public VideoPromptService(ChatClientRegistry clientRegistry,
                              VideoPromptTemplateMapper templateMapper) {
        this.clientRegistry = clientRegistry;
        this.templateMapper = templateMapper;
    }

    /**
     * Enhance a user's brief prompt into a detailed video-generation prompt using LLM.
     * @return EnhanceResult containing the enhanced prompt and suggested negative tags.
     */
    public EnhanceResult enhance(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        String systemPrompt = """
                你是一位资深的AI视频生成提示词工程师，擅长为即梦/Seedance等视频模型编写高质量提示词。

                请将用户提供的简短描述扩展为详细的视频生成提示词。扩展时需包含以下要素（如适用）：
                1. 镜头语言：景别（特写/中景/远景）、镜头角度（俯拍/仰拍/平视）、运镜方式（推拉摇移跟）
                2. 光线与色调：光源方向、色温（暖/冷/中性）、对比度、影调风格
                3. 主体与动作：主体外观特征、动作节奏（缓慢流畅/快速激烈）
                4. 场景与氛围：环境描述、天气/时间、情绪基调
                5. 风格：写实、电影感、动漫、赛博朋克等

                要求：
                - 使用中文输出（适配即梦 Seedance 模型）
                - 控制在 200 字以内
                - 直接输出最终提示词，不要解释
                - 同时给出 3-5 个建议的反向提示词（逗号分隔），放在 [NEGATIVE] 标记之后

                用户输入：{USER_PROMPT}

                输出格式：
                {增强后的提示词}
                [NEGATIVE] {反向提示词1}, {反向提示词2}, ...
                """.replace("{USER_PROMPT}", userPrompt);

        try {
            String raw = clientRegistry.getDefault().prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                throw new RuntimeException("LLM 返回空结果");
            }

            // Parse the response: split on [NEGATIVE] marker
            String enhanced = raw;
            String suggestedNegative = "";
            int negIdx = raw.indexOf("[NEGATIVE]");
            if (negIdx >= 0) {
                enhanced = raw.substring(0, negIdx).trim();
                suggestedNegative = raw.substring(negIdx + "[NEGATIVE]".length()).trim();
            }

            log.info("Prompt enhanced: {} chars -> {} chars, suggested negative: {}",
                    userPrompt.length(), enhanced.length(), suggestedNegative);
            return new EnhanceResult(enhanced, suggestedNegative.isEmpty() ? null : suggestedNegative);
        } catch (Exception e) {
            log.error("Prompt enhancement failed", e);
            throw new RuntimeException("增强失败: " + e.getMessage(), e);
        }
    }

    /**
     * Translate/polish a prompt. target: "en" / "zh" / "auto"
     */
    public String translate(String prompt, String target) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("提示词不能为空");
        }

        String systemPrompt = switch (target != null ? target : "auto") {
            case "en" -> "将以下中文精准翻译为英文视频生成提示词，保持原意的同时让表达更符合AI视频模型的习惯用语。只输出英文结果，不要解释。";
            case "zh" -> "将以下英文视频生成提示词翻译为中文，保持专业术语的准确性。只输出中文结果，不要解释。";
            default -> "将以下中英混杂的视频生成提示词统一润色为地道的中文表达，保持所有要素（镜头、光线、动作、风格）不丢失。只输出润色结果，不要解释。";
        };

        try {
            String result = clientRegistry.getDefault().prompt()
                    .system(systemPrompt)
                    .user(prompt)
                    .call()
                    .content();

            if (result == null || result.isBlank()) {
                throw new RuntimeException("LLM 返回空结果");
            }
            log.info("Prompt translated: target={}, {} chars -> {} chars", target, prompt.length(), result.length());
            return result.trim();
        } catch (Exception e) {
            log.error("Prompt translation failed", e);
            throw new RuntimeException("翻译失败: " + e.getMessage(), e);
        }
    }

    /**
     * Get all templates, ordered by category then sortOrder.
     */
    public List<VideoPromptTemplate> getTemplates() {
        List<VideoPromptTemplate> all = templateMapper.selectList(null);
        all.sort(Comparator.comparing(VideoPromptTemplate::getCategory)
                .thenComparing(VideoPromptTemplate::getSortOrder));
        return all;
    }

    /**
     * Create a user-defined template.
     */
    @Transactional
    public VideoPromptTemplate createTemplate(String name, String content, String category) {
        VideoPromptTemplate t = new VideoPromptTemplate();
        t.setId(UUID.randomUUID().toString());
        t.setName(name);
        t.setContent(content);
        t.setCategory(category != null ? category : DEFAULT_CATEGORY);
        t.setSortOrder(DEFAULT_SORT_ORDER);
        t.setIsPreset(false);
        t.setCreatedAt(java.time.LocalDateTime.now());
        t.setUpdatedAt(java.time.LocalDateTime.now());
        templateMapper.insert(t);
        return t;
    }

    /**
     * Delete a template. Preset templates (isPreset=true) cannot be deleted.
     */
    @Transactional
    public void deleteTemplate(String id) {
        VideoPromptTemplate t = templateMapper.selectById(id);
        if (t == null) {
            throw new NoSuchElementException("模板不存在: " + id);
        }
        if (Boolean.TRUE.equals(t.getIsPreset())) {
            throw new IllegalArgumentException("预设模板不可删除");
        }
        templateMapper.deleteById(id);
    }

    /**
     * Result of prompt enhancement.
     */
    public record EnhanceResult(String enhanced, String suggestedNegative) {}
}
