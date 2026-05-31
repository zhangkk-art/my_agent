package com.myagent.rag.service;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentParserService {

    private static final int CHUNK_SIZE = 800;   // tokens per chunk
    private static final int CHUNK_OVERLAP = 100; // overlap between chunks

    private final TokenTextSplitter splitter;

    public DocumentParserService() {
        this.splitter = new TokenTextSplitter();
        // Default chunk size=800, overlap=100 in Spring AI 1.1.x via builder
    }

    /**
     * Parse any supported document (PDF, Word, Markdown, TXT, HTML, etc.)
     * and extract plain text using Apache Tika's auto-detection.
     */
    public String parseToString(InputStream in, String fileName) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // no write limit
        Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName);
        ParseContext context = new ParseContext();

        try {
            parser.parse(in, handler, metadata, context);
        } finally {
            in.close();
        }

        return handler.toString().trim();
    }

    /**
     * Split text into overlapping chunks using token-aware splitting.
     */
    public List<String> chunkText(String text) {
        List<org.springframework.ai.document.Document> docs = splitter.apply(
                List.of(new org.springframework.ai.document.Document(text)));
        return docs.stream()
                .map(org.springframework.ai.document.Document::getText)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
    }
}
