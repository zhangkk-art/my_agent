package com.myagent.controller;

import com.myagent.config.ChatClientRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DailyQuestionController {

    private static final Logger log = LoggerFactory.getLogger(DailyQuestionController.class);
    private static final String PROMPT =
            "请生成20个今日推荐问题，要求：\n" +
            "1. 覆盖编程技术、科学知识、生活技巧、创意写作、历史文化、职场成长等不同领域\n" +
            "2. 每个问题简洁有趣，适合作为 AI 对话的开场话题\n" +
            "3. 每行一个问题，不加序号、不加标点前缀，直接输出问题内容\n" +
            "4. 问题要有新意，避免过于常见或重复的套路";

    private final ChatClientRegistry clientRegistry;

    // Simple day-level in-memory cache — no DB needed, regenerates on restart
    private volatile String cachedDate = "";
    private volatile List<String> cachedQuestions = null;

    public DailyQuestionController(ChatClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @GetMapping("/questions/daily")
    public List<String> getDailyQuestions() {
        String today = LocalDate.now().toString();
        if (today.equals(cachedDate) && cachedQuestions != null) {
            return cachedQuestions;
        }
        log.info("Generating daily questions for {}", today);
        try {
            String raw = clientRegistry.getDefault().prompt().user(PROMPT).call().content();
            List<String> questions = Arrays.stream(raw.split("\n"))
                    .map(String::trim)
                    .filter(s -> s.length() > 4 && !s.startsWith("#"))
                    .limit(20)
                    .collect(Collectors.toList());
            if (!questions.isEmpty()) {
                cachedDate = today;
                cachedQuestions = questions;
                log.info("Daily questions cached: {} items", questions.size());
            }
            return questions;
        } catch (Exception e) {
            log.warn("Failed to generate daily questions", e);
            return List.of();
        }
    }
}
