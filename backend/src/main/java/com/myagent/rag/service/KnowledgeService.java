package com.myagent.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.myagent.rag.mapper.KnowledgeDocumentMapper;
import com.myagent.rag.model.KnowledgeDocument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final String INDEX_NAME = "knowledge_chunks";
    private static final int TOP_K = 5;
    private static final int FETCH_SIZE = TOP_K * 3; // over-fetch then re-rank by recency
    private static final int EMBEDDING_DIMS = 1536;

    private final ElasticsearchClient es;
    private final EmbeddingModel embeddingModel;
    private final DocumentParserService parserService;
    private final KnowledgeDocumentMapper docMapper;
    private final CacheManager cacheManager;

    @Value("${app.elasticsearch.kb-dir:./knowledge-base}")
    private String kbDirPath;

    public KnowledgeService(ElasticsearchClient es,
                            EmbeddingModel embeddingModel,
                            DocumentParserService parserService,
                            KnowledgeDocumentMapper docMapper,
                            CacheManager cacheManager) {
        this.es = es;
        this.embeddingModel = embeddingModel;
        this.parserService = parserService;
        this.docMapper = docMapper;
        this.cacheManager = cacheManager;
    }

    @PostConstruct
    public void initIndex() {
        try {
            ExistsRequest exists = new ExistsRequest.Builder().index(INDEX_NAME).build();
            if (!es.indices().exists(exists).value()) {
                CreateIndexRequest req = new CreateIndexRequest.Builder()
                        .index(INDEX_NAME)
                        .mappings(m -> m
                                .properties("doc_id", p -> p.keyword(k -> k))
                                .properties("doc_name", p -> p.keyword(k -> k))
                                .properties("chunk_index", p -> p.integer(i -> i))
                                .properties("text", p -> p.text(t -> t.analyzer("standard")))
                                .properties("embedding", p -> p
                                        .denseVector(dv -> dv
                                                .dims(EMBEDDING_DIMS)
                                                .index(true)
                                                .similarity("cosine")))
                                .properties("created_at", p -> p.date(d -> d)))
                        .build();
                es.indices().create(req);
                log.info("Elasticsearch index '{}' created", INDEX_NAME);
            }
            // Auto-load documents from kb-dir on startup
            loadFromDirectory();
        } catch (Exception e) {
            log.error("Failed to initialize ES index '{}': {}", INDEX_NAME, e.getMessage());
        }
    }

    /**
     * Scan kb-dir and sync with the index:
     * - New files → index them
     * - Changed files (hash mismatch) → delete old chunks and re-index
     * - Files removed from disk → delete their DB record and ES chunks
     */
    public RefreshResult loadFromDirectory() {
        Path dir = Paths.get(kbDirPath);
        if (!Files.isDirectory(dir)) {
            try {
                Files.createDirectories(dir);
                log.info("Knowledge base directory created: {}", dir.toAbsolutePath());
            } catch (IOException e) {
                log.warn("Cannot create kb-dir '{}': {}", kbDirPath, e.getMessage());
            }
            return new RefreshResult(0, 0, 0);
        }

        // Build name → document map from DB
        Map<String, KnowledgeDocument> indexedByName = docMapper.selectList(null).stream()
                .collect(Collectors.toMap(KnowledgeDocument::getName, d -> d));

        int added = 0, updated = 0, removed = 0;

        // Collect all filenames currently on disk
        Set<String> diskFiles = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) continue;
                String name = path.getFileName().toString();
                diskFiles.add(name);

                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String hash = md5Hex(bytes);
                    String contentType = Files.probeContentType(path);
                    if (contentType == null) contentType = "application/octet-stream";

                    KnowledgeDocument existing = indexedByName.get(name);
                    if (existing == null) {
                        // New file
                        uploadDocumentBytes(name, contentType, bytes, hash);
                        added++;
                        log.info("Knowledge base: indexed new file '{}'", name);
                    } else if (!hash.equals(existing.getFileHash())) {
                        // File content changed — delete old and re-index
                        log.info("Knowledge base: '{}' changed (hash mismatch), re-indexing...", name);
                        deleteDocument(existing.getId());
                        uploadDocumentBytes(name, contentType, bytes, hash);
                        updated++;
                    } else {
                        log.debug("Knowledge base: '{}' unchanged, skipping", name);
                    }
                } catch (Exception e) {
                    log.error("Failed to process file '{}': {}", name, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan kb-dir '{}': {}", kbDirPath, e.getMessage());
        }

        // Remove DB/ES records for files that no longer exist on disk
        for (Map.Entry<String, KnowledgeDocument> entry : indexedByName.entrySet()) {
            if (!diskFiles.contains(entry.getKey())) {
                try {
                    deleteDocument(entry.getValue().getId());
                    removed++;
                    log.info("Knowledge base: removed stale record for deleted file '{}'", entry.getKey());
                } catch (Exception e) {
                    log.error("Failed to remove stale record for '{}': {}", entry.getKey(), e.getMessage());
                }
            }
        }

        log.info("Knowledge base sync complete: +{} added, ~{} updated, -{} removed", added, updated, removed);
        RefreshResult result = new RefreshResult(added, updated, removed);
        // Evict document list cache if anything changed so next GET reads fresh data
        if (added > 0 || updated > 0 || removed > 0) {
            Cache cache = cacheManager.getCache("knowledge_documents");
            if (cache != null) cache.evict("all");
        }
        return result;
    }

    /** Compute MD5 hex string from raw bytes. */
    private String md5Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public record RefreshResult(int added, int updated, int removed) {}

    /** Entry point for multipart upload from the controller. */
    @Transactional
    @CacheEvict(value = "knowledge_documents", key = "'all'")
    public KnowledgeDocument uploadDocument(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String hash = md5Hex(bytes);
        return uploadDocumentBytes(file.getOriginalFilename(), file.getContentType(), bytes, hash);
    }

    /** Entry point for InputStream-based upload (kept for backward compat). */
    @Transactional
    @CacheEvict(value = "knowledge_documents", key = "'all'")
    public KnowledgeDocument uploadDocument(String fileName, String contentType, long fileSize, InputStream in) throws Exception {
        byte[] bytes = in.readAllBytes();
        String hash = md5Hex(bytes);
        return uploadDocumentBytes(fileName, contentType, bytes, hash);
    }

    /** Core indexing logic — parses bytes, chunks, embeds, and persists to DB + ES. */
    @Transactional
    public KnowledgeDocument uploadDocumentBytes(String fileName, String contentType, byte[] bytes, String hash) throws Exception {
        String text = parserService.parseToString(new java.io.ByteArrayInputStream(bytes), fileName);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Document contains no extractable text");
        }

        List<String> chunks = parserService.chunkText(text);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document is too short to chunk");
        }

        List<float[]> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
            embeddings.add(embeddingModel.embed(chunk));
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setName(fileName);
        doc.setContentType(contentType);
        doc.setFileSize((long) bytes.length);
        doc.setChunkCount(chunks.size());
        doc.setFileHash(hash);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkDoc = new LinkedHashMap<>();
            chunkDoc.put("doc_id", doc.getId());
            chunkDoc.put("doc_name", doc.getName());
            chunkDoc.put("chunk_index", i);
            chunkDoc.put("text", chunks.get(i));
            chunkDoc.put("embedding", toDoubleList(embeddings.get(i)));
            chunkDoc.put("created_at", LocalDateTime.now().toString());
            final int idx = i;
            bulkBuilder.operations(op -> op
                    .index(ix -> ix.index(INDEX_NAME).id(doc.getId() + "_" + idx).document(chunkDoc)));
        }
        es.bulk(bulkBuilder.build());
        log.info("Indexed: {} ({} chunks, hash={})", doc.getName(), chunks.size(), hash);
        return doc;
    }

    @Cacheable(value = "knowledge_documents", key = "'all'")
    public List<KnowledgeDocument> listDocuments() {
        return docMapper.selectList(null);
    }

    @Transactional
    @CacheEvict(value = "knowledge_documents", key = "'all'")
    public void deleteDocument(String id) throws Exception {
        docMapper.deleteById(id);
        // Delete all ES chunks for this document
        es.deleteByQuery(dq -> dq
                .index(INDEX_NAME)
                .query(q -> q.term(t -> t.field("doc_id").value(id))));
    }

    /**
     * Hybrid search: BM25 + kNN, then re-ranked with a recency decay multiplier.
     * Fetches FETCH_SIZE candidates from ES, applies time decay in Java, returns top TOP_K.
     */
    public List<SearchResult> search(String query) throws Exception {
        float[] queryVec = embeddingModel.embed(query);

        Query knnQuery = Query.of(q -> q
                .scriptScore(ss -> ss
                        .query(sq -> sq.matchAll(ma -> ma))
                        .script(s -> s
                                .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                .params("query_vector", JsonData.of(toDoubleList(queryVec))))));

        Query textQuery = Query.of(q -> q
                .match(m -> m.field("text").query(query)));

        // Over-fetch so recency re-ranking has enough candidates to choose from
        SearchRequest searchReq = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .query(q -> q.bool(b -> b
                        .should(textQuery)
                        .should(knnQuery)))
                .size(FETCH_SIZE)
                .build();

        SearchResponse<Map> response = es.search(searchReq, Map.class);

        return response.hits().hits().stream()
                .map(hit -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> src = (Map<String, Object>) hit.source();
                    double esScore = hit.score() != null ? hit.score() : 0.0;
                    String createdAt = (String) src.get("created_at");
                    // Final score = semantic relevance × recency multiplier
                    double finalScore = esScore * recencyMultiplier(createdAt);
                    return new SearchResult(
                            (String) src.get("doc_name"),
                            (String) src.get("text"),
                            finalScore);
                })
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(TOP_K)
                .collect(Collectors.toList());
    }

    /**
     * Recency multiplier: 1.0 within the first 7 days,
     * linearly decays to 0.5 at 90 days, stays at 0.5 beyond that.
     * Keeps newer chunks competitive when two documents cover the same topic.
     */
    private double recencyMultiplier(String createdAtStr) {
        if (createdAtStr == null) return 1.0;
        try {
            LocalDateTime createdAt = LocalDateTime.parse(createdAtStr);
            long days = ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
            if (days <= 7) return 1.0;
            if (days >= 90) return 0.5;
            return 1.0 - 0.5 * (days - 7.0) / (90.0 - 7.0);
        } catch (Exception e) {
            return 1.0;
        }
    }

    private static List<Double> toDoubleList(float[] vec) {
        List<Double> list = new ArrayList<>(vec.length);
        for (float v : vec) list.add((double) v);
        return list;
    }

    /**
     * Search result DTO.
     */
    public record SearchResult(String docName, String text, double score) {}
}
