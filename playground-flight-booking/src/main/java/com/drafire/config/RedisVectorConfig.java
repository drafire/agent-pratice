package com.drafire.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import redis.clients.jedis.JedisPooled;

@Configuration
public class RedisVectorConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.ai.vectorstore.redis.prefix:flight_knowledge}")
    private String prefix;

    @Bean
    public JedisPooled jedisPooled() {
        return new JedisPooled(redisHost, redisPort);
    }

    @Bean
    @Primary
    public VectorStore redisVectorStore(EmbeddingModel embeddingModel, JedisPooled jedisPooled) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .prefix(prefix)
                .build();
    }
}