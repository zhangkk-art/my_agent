# RAG Knowledge Base Agent — Design Spec

**Date:** 2026-05-31
**Status:** draft

---

## Overview

Add a RAG (Retrieval-Augmented Generation) knowledge base to the existing AI chat app.
Users upload documents (PDF/Word/Markdown/TXT), which are parsed, chunked, embedded,
and stored in Elasticsearch. When chatting, relevant chunks are retrieved via hybrid
search (BM25 + kNN vector) and injected into the LLM context for grounded answers with
source citations.

## Tech Stack Decisions

| Component | Choice | Reason |
|-----------|--------|--------|
| Vector store | Elasticsearch 8.x | BM25 + kNN hybrid search, production-grade, resume gold |
| Document parsing | Apache Tika 2.x | 20+ formats supported out of the box |
| Text chunking | Spring AI TokenTextSplitter | Token-aware splitting with overlap |
| Embedding | DashScope text-embedding-v2 | Reuse existing DashScope key |
| Document metadata | MySQL | Existing DB, no extra infra needed |

## Data Flow

```
Upload: multipart file → Tika parse → raw text
  → TokenTextSplitter (chunk size=800 tokens, overlap=100)
  → DashScope embed each chunk → [float[] vector]
  → ES bulk index (index: "knowledge_chunks")
  → MySQL insert document record (id, name, type, size, chunk_count, created_at)

Chat: user question → DashScope embed → vector
  → ES hybrid search (BM25 text query + kNN vector, weighted fusion)
  → Top-5 chunks returned (text + source doc name + score)
  → Inject chunks into system prompt as context
  → LLM stream response with inline citations
```

## ES Index Mapping

```json
{
  "knowledge_chunks": {
    "mappings": {
      "properties": {
        "doc_id": { "type": "keyword" },
        "doc_name": { "type": "keyword" },
        "chunk_index": { "type": "integer" },
        "text": { "type": "text", "analyzer": "standard" },
        "embedding": { "type": "dense_vector", "dims": 1536, "index": true, "similarity": "cosine" },
        "created_at": { "type": "date" }
      }
    }
  }
}
```

dims=1536 for DashScope text-embedding-v2.

## Database Table (MySQL)

```sql
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    file_size BIGINT DEFAULT 0,
    chunk_count INT DEFAULT 0,
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## Backend API

### Document Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/knowledge/documents` | Upload document (multipart form, field: "file") |
| GET | `/api/knowledge/documents` | List all documents |
| DELETE | `/api/knowledge/documents/{id}` | Delete document + its ES chunks |

### RAG Chat

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/knowledge/chat` | RAG chat (SSE stream), body: `{message, model}` |

## Backend Files

| File | Action | Purpose |
|------|--------|---------|
| `rag/model/KnowledgeDocument.java` | Create | Entity for MySQL |
| `rag/mapper/KnowledgeDocumentMapper.java` | Create | MyBatis-Plus mapper |
| `rag/service/DocumentParserService.java` | Create | Tika parse + chunk |
| `rag/service/KnowledgeService.java` | Create | RAG pipeline orchestration |
| `rag/controller/KnowledgeController.java` | Create | REST controller |
| `config/ElasticsearchConfig.java` | Create | ES RestClient bean |

## Maven Dependencies

Add to pom.xml:
- `spring-ai-elasticsearch-store-spring-boot-starter` (Spring AI ES vector store)
- `org.apache.tika:tika-core:2.9.2`
- `org.apache.tika:tika-parsers-standard-package:2.9.2`
- `co.elastic.clients:elasticsearch-java:8.15.0`

Or use Spring AI's built-in ElasticsearchVectorStore which handles the ES client internally.

## Frontend Changes

**ChatArea.vue / ChatInput.vue:**
- Add a knowledge base status bar above the chat input
- Shows: "📚 知识库 · N 篇文档" + upload button
- Upload button triggers file picker, accepts .pdf/.docx/.md/.txt
- After upload: Toast success + refresh document count

**MessageBubble.vue:**
- When assistant message has `citations` metadata, render source links at the bottom:
  ```
  📎 参考来源:
  · document-name.md (片段 #3, 相似度 89%)
  · another-doc.pdf (片段 #1, 相似度 76%)
  ```

## Scope

**In scope:** Document upload/parse/chunk/embed/index, hybrid search, RAG streaming chat with citations, document list/delete

**Out of scope:** User authentication, knowledge base multi-tenancy, incremental document updates, PDF OCR (images inside PDFs), custom chunk size per document
