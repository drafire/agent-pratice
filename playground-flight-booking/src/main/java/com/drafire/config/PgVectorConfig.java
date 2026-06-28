package com.drafire.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class PgVectorConfig {

    @Value("${spring.datasource.url}")
    private String mysqlUrl;

    @Value("${spring.datasource.username}")
    private String mysqlUsername;

    @Value("${spring.datasource.password}")
    private String mysqlPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String mysqlDriverClassName;

    @Value("${pgvector.datasource.url}")
    private String pgUrl;

    @Value("${pgvector.datasource.username}")
    private String pgUsername;

    @Value("${pgvector.datasource.password}")
    private String pgPassword;

    @Value("${pgvector.datasource.driver-class-name}")
    private String pgDriverClassName;

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.pgvector.index-type:hnsw}")
    private String indexType;

    @Value("${spring.ai.vectorstore.pgvector.distance-type:cosine_distance}")
    private String distanceType;

    @Value("${spring.ai.vectorstore.pgvector.initialize-schema:false}")
    private boolean initializeSchema;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysqlUrl);
        config.setUsername(mysqlUsername);
        config.setPassword(mysqlPassword);
        config.setDriverClassName(mysqlDriverClassName);
        config.setConnectionTimeout(10000);
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean(name = "pgVectorDataSource")
    public DataSource pgVectorDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(pgUrl);
        config.setUsername(pgUsername);
        config.setPassword(pgPassword);
        config.setDriverClassName(pgDriverClassName);
        config.setConnectionTimeout(10000);
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean(name = "pgVectorJdbcTemplate")
    public JdbcTemplate pgVectorJdbcTemplate() {
        return new JdbcTemplate(pgVectorDataSource());
    }

    @Bean
    public PgVectorStore pgVectorStore(EmbeddingModel embeddingModel) {
        PgVectorStore.PgIndexType pgIndexType = "ivfflat".equalsIgnoreCase(indexType)
                ? PgVectorStore.PgIndexType.IVFFLAT
                : PgVectorStore.PgIndexType.HNSW;

        PgVectorStore.PgDistanceType pgDistanceType = "cosine_distance".equalsIgnoreCase(distanceType)
                ? PgVectorStore.PgDistanceType.COSINE_DISTANCE
                : PgVectorStore.PgDistanceType.EUCLIDEAN_DISTANCE;

        return PgVectorStore.builder(pgVectorJdbcTemplate(), embeddingModel)
                .dimensions(dimensions)
                .indexType(pgIndexType)
                .distanceType(pgDistanceType)
                .initializeSchema(initializeSchema)
                .build();
    }
}