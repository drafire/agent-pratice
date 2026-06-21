package com.drafire.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class KnowledgeBaseVectorStoreManager {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseVectorStoreManager.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeBaseProperties properties;

    private final Map<String, PgVectorStore> storeMap = new LinkedHashMap<>();

    public KnowledgeBaseVectorStoreManager(JdbcTemplate jdbcTemplate,
                                           EmbeddingModel embeddingModel,
                                           KnowledgeBaseProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        for (KnowledgeBaseProperties.StoreConfig config : properties.getStores()) {
            initSchemaTable(config.getTableName(), config.getDimensions());

            PgVectorStore.PgIndexType indexType = parseIndexType(config.getIndexType());

            PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .vectorTableName(config.getTableName())
                    .dimensions(config.getDimensions())
                    .indexType(indexType)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .initializeSchema(true)
                    .build();

            storeMap.put(config.getScope(), store);
            logger.info("知识库向量存储初始化完成: scope={}, table={}, dimensions={}, indexType={}",
                    config.getScope(), config.getTableName(), config.getDimensions(), config.getIndexType());
        }
    }

    public PgVectorStore getStore(String scope) {
        return storeMap.computeIfAbsent(scope, key -> {
            KnowledgeBaseProperties.StoreConfig config = properties.getStores().stream()
                    .filter(s -> s.getScope().equals(key))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未知的知识库: " + key));

            initSchemaTable(config.getTableName(), config.getDimensions());

            PgVectorStore.PgIndexType indexType = parseIndexType(config.getIndexType());

            PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .vectorTableName(config.getTableName())
                    .dimensions(config.getDimensions())
                    .indexType(indexType)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .initializeSchema(true)
                    .build();

            logger.info("知识库向量存储初始化完成2: scope={}, table={}, dimensions={}, indexType={}",
                    config.getScope(), config.getTableName(), config.getDimensions(), config.getIndexType());
            return store;
        });
    }

    public Map<String, PgVectorStore> getAllStores() {
        return storeMap;
    }

    private PgVectorStore.PgIndexType parseIndexType(String indexType) {
        if ("ivfflat".equalsIgnoreCase(indexType)) {
            return PgVectorStore.PgIndexType.IVFFLAT;
        }
        return PgVectorStore.PgIndexType.HNSW;
    }

    private void initSchemaTable(String tableName, int dimensions) {
        try {
            ClassPathResource resource = new ClassPathResource("embedding/schema-pgvector.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            sql = sql.replace("${tableName}", tableName)
                     .replace("${dimensions}", String.valueOf(dimensions));
            jdbcTemplate.execute(sql);
            logger.info("向量表创建成功: table={}", tableName);
        } catch (Exception e) {
            logger.error("向量表创建失败: table={}", tableName, e);
            throw new RuntimeException("向量表创建失败: " + tableName, e);
        }
    }
}