package com.myagent.rag.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.JsonData;
import com.myagent.rag.mapper.KnowledgeDocumentMapper;
import com.myagent.rag.model.KnowledgeDocument;
import com.myagent.service.ChatService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final String INDEX_NAME = "knowledge_chunks";
    private static final int TOP_K = 5;
    private static final int EMBEDDING_DIMS = 1536;

    private final ElasticsearchClient es;
    private final EmbeddingModel embeddingModel;
    private final DocumentParserService parserService;
    private final KnowledgeDocumentMapper docMapper;

    public KnowledgeService(ElasticsearchClient es,
                            EmbeddingModel embeddingModel,
                            DocumentParserService parserService,
                            KnowledgeDocumentMapper docMapper) {
        this.es = es;
        this.embeddingModel = embeddingModel;
        this.parserService = parserService;
        this.docMapper = docMapper;
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
        } catch (Exception e) {
            log.error("Failed to initialize ES index '{}': {}", INDEX_NAME, e.getMessage());
        }
    }

    @Transactional
    public KnowledgeDocument uploadDocument(MultipartFile file) throws Exception {
        // 1. Parse document to raw text
        String text = parserService.parseToString(file.getInputStream(), file.getOriginalFilename());
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Document contains no extractable text");
        }

        // 2. Chunk text
        List<String> chunks = parserService.chunkText(text);
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("Document is too short to chunk");
        }

        // 3. Embed all chunks
        List<float[]> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
            float[] vec = embeddingModel.embed(chunk);
            embeddings.add(vec);
        }

        // 4. Save document record to MySQL
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(UUID.randomUUID().toString());
        doc.setName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setChunkCount(chunks.size());
        doc.setCreatedAt(LocalDateTime.now());
        docMapper.insert(doc);

        // 5. Bulk index to ES
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
                    .index(ix -> ix
                            .index(INDEX_NAME)
                            .id(doc.getId() + "_" + idx)
                            .document(chunkDoc)));
        }
        es.bulk(bulkBuilder.build());
        log.info("Document '{}' indexed: {} chunks", doc.getName(), chunks.size());

        return doc;
    }

    public List<KnowledgeDocument> listDocuments() {
        return docMapper.selectList(null);
    }

    @Transactional
    public void deleteDocument(String id) throws Exception {
        docMapper.deleteById(id);
        // Delete all ES chunks for this document
        es.deleteByQuery(dq -> dq
                .index(INDEX_NAME)
                .query(q -> q.term(t -> t.field("doc_id").value(id))));
    }

    /**
     * Hybrid search: BM25 on text + kNN on embedding, weighted fusion.
     */
    public List<SearchResult> search(String query) throws Exception {
        // Embed query
        float[] queryVec = embeddingModel.embed(query);

        // Build kNN query clause
        Query knnQuery = Query.of(q -> q
                .scriptScore(ss -> ss
                        .query(sq -> sq.matchAll(ma -> ma))
                        .script(s -> s
                                .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                                .params("query_vector", JsonData.of(toDoubleList(queryVec))))));

        // Build BM25 text query
        Query textQuery = Query.of(q -> q
                .match(m -> m.field("text").query(query)));

        // Combine: both contribute
        SearchRequest searchReq = new SearchRequest.Builder()
                .index(INDEX_NAME)
                .query(q -> q.bool(b -> b
                        .should(textQuery)
                        .should(knnQuery)))
                .size(TOP_K)
                .build();

        SearchResponse<Map> response = es.search(searchReq, Map.class);

        return response.hits().hits().stream()
                .map(hit -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> src = (Map<String, Object>) hit.source();
                    return new SearchResult(
                            (String) src.get("doc_name"),
                            (String) src.get("text"),
                            hit.score() != null ? hit.score() : 0.0);
                })
                .collect(Collectors.toList());
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
