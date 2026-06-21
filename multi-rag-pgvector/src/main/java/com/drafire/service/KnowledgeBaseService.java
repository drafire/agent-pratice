package com.drafire.service;

import com.drafire.config.KnowledgeBaseProperties;
import com.drafire.config.KnowledgeBaseVectorStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseVectorStoreManager storeManager;
    private final KnowledgeBaseProperties properties;
    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseService(KnowledgeBaseVectorStoreManager storeManager,
                                KnowledgeBaseProperties properties,
                                ChatModel chatModel,
                                JdbcTemplate jdbcTemplate) {
        this.storeManager = storeManager;
        this.properties = properties;
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    public String importText(String scope, String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        PgVectorStore store = storeManager.getStore(scope);
        List<Document> documents = List.of(new Document(text));
        List<Document> chunks = new TokenTextSplitter().apply(documents);

        store.add(chunks);
        logger.info("文本导入知识库成功: scope={}, chunks={}", scope, chunks.size());
        return "success";
    }

    public String importFiles(String scope, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("必须上传非空的文件");
        }

        PgVectorStore store = storeManager.getStore(scope);

        List<Document> documentList = new TikaDocumentReader(file.getResource()).get();
        documentList = new ContentFormatTransformer(DefaultContentFormatter.defaultConfig()).apply(documentList);

        TokenTextSplitter tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(150)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(2000)
                .withKeepSeparator(true)
                .build();

        List<Document> chunks = tokenTextSplitter.apply(documentList);

        KeywordMetadataEnricher keywordMetadataEnricher = KeywordMetadataEnricher.builder(chatModel)
                .keywordCount(3)
                .keywordsTemplate(new PromptTemplate("""
                        你是一个关键词提取器。从以下文本中提取恰好 %s 个最重要的中文关键词。
                        严格要求：只返回 %s 个关键词，以逗号分隔，不要有任何其他内容。
                        文本：
                        {context_str}
                        """))
                .build();
        chunks = keywordMetadataEnricher.apply(chunks);

        chunks.forEach(doc -> {
            Object kw = doc.getMetadata().get("excerpt_keywords");
            if (kw != null) {
                String cleaned = kw.toString()
                        .replaceAll("[\\n\\r]+", ",")
                        .replaceAll("[，、；;]+", ",")
                        .replaceAll("\\d+[、.．]\\s*", "")
                        .replaceAll("^[：:\\s,，]+", "")
                        .replaceAll("[\\s,，]+$", "");
                String[] parts = cleaned.split("\\s*,\\s*");
                List<String> keywords = Arrays.stream(parts)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(3)
                        .toList();
                doc.getMetadata().put("excerpt_keywords", String.join(",", keywords));
            }
        });

        store.add(chunks);
        logger.info("文件导入知识库成功: scope={}, fileName={}, chunks={}",
                scope, file.getOriginalFilename(), chunks.size());
        return "success";
    }

    public List<Document> multiRecall(String query, List<String> scopes) {
        List<CompletableFuture<List<Document>>> futures = scopes.stream()
                .flatMap(scope -> {
                    KnowledgeBaseProperties.StoreConfig config = findStoreConfig(scope);
                    CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(() -> {
                        PgVectorStore store = storeManager.getStore(scope);
                        SearchRequest request = SearchRequest.builder()
                                .query(query)
                                .topK(config.getTopK())
                                .similarityThreshold(config.getSimilarityThreshold())
                                .build();
                        List<Document> docs = store.similaritySearch(request);
                        docs.forEach(doc -> doc.getMetadata().put("source_scope", scope));
                        docs.forEach(doc -> doc.getMetadata().put("recall_method", "vector"));
                        logger.info("向量召回: scope={}, 命中={}", scope, docs.size());
                        return docs;
                    });
                    CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(() -> {
                        List<Document> docs = keywordSearch(query, config.getTableName(),
                                config.getTopK(), config.getSimilarityThreshold());
                        docs.forEach(doc -> doc.getMetadata().put("source_scope", scope));
                        docs.forEach(doc -> doc.getMetadata().put("recall_method", "keyword"));
                        logger.info("关键词召回: scope={}, 命中={}", scope, docs.size());
                        return docs;
                    });
                    return List.of(vectorFuture, keywordFuture).stream();
                })
                .toList();

        List<Document> allDocs = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Map<String, Document> unique = new LinkedHashMap<>();
        for (Document doc : allDocs) {
            String key = doc.getText().trim();
            Document existing = unique.get(key);
            if (existing == null || getScore(doc) > getScore(existing)) {
                unique.put(key, doc);
            }
        }

        List<Document> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> Double.compare(getScore(b), getScore(a)));
        logger.info("多路召回完成: 总命中={}, 去重后={}", allDocs.size(), result.size());
        return result;
    }

    private List<Document> keywordSearch(String query, String tableName, int topK, double threshold) {
        String sql = """
                SELECT id, content, metadata, similarity(content, ?) AS score
                FROM %s
                WHERE similarity(content, ?) > ?
                ORDER BY score DESC
                LIMIT ?
                """.formatted(tableName);
        return jdbcTemplate.query(sql,
                ps -> {
                    ps.setString(1, query);
                    ps.setString(2, query);
                    ps.setDouble(3, threshold);
                    ps.setInt(4, topK);
                },
                this::mapRow);
    }

    private Document mapRow(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> metadata = new HashMap<>();
        String metadataJson = rs.getString("metadata");
        if (metadataJson != null && !metadataJson.isEmpty()) {
            metadata.put("raw", metadataJson);
        }
        metadata.put("distance", rs.getDouble("score"));
        return new Document(rs.getString("id"), rs.getString("content"), metadata);
    }

    private KnowledgeBaseProperties.StoreConfig findStoreConfig(String scope) {
        return properties.getStores().stream()
                .filter(s -> s.getScope().equals(scope))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的知识库: " + scope));
    }

    private double getScore(Document doc) {
        Object score = doc.getMetadata().get("distance");
        if (score instanceof Number) {
            return ((Number) score).doubleValue();
        }
        return 0.0;
    }
}