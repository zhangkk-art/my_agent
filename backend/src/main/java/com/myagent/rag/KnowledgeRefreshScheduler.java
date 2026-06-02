package com.myagent.rag;

import com.myagent.rag.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically syncs the knowledge-base directory with Elasticsearch.
 * Detects new/changed/deleted files without restarting the server.
 * Disable via: app.elasticsearch.refresh-enabled=false
 */
@Component
@ConditionalOnProperty(name = "app.elasticsearch.refresh-enabled", havingValue = "true", matchIfMissing = true)
public class KnowledgeRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRefreshScheduler.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeRefreshScheduler(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    // fixedDelay: wait for previous run to finish before scheduling next
    @Scheduled(fixedDelayString = "${app.elasticsearch.refresh-interval-ms:300000}")
    public void refresh() {
        log.debug("Knowledge base scheduled refresh starting...");
        try {
            KnowledgeService.RefreshResult result = knowledgeService.loadFromDirectory();
            if (result.added() > 0 || result.updated() > 0 || result.removed() > 0) {
                log.info("Knowledge base refresh: +{} added, ~{} updated, -{} removed",
                        result.added(), result.updated(), result.removed());
            }
        } catch (Exception e) {
            log.error("Knowledge base scheduled refresh failed: {}", e.getMessage());
        }
    }
}
