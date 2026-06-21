package com.drafire.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class KnowledgeService {
    public static final Logger logger = LoggerFactory.getLogger(KnowledgeService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public KnowledgeService(VectorStore vectorStore,ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        DefaultChatOptions defaultChatOptions = new DefaultChatOptions();
        defaultChatOptions.setMaxTokens(1000);
        defaultChatOptions.setTopK(5);
        defaultChatOptions.setTemperature(0.5);
        this.chatClient =chatClientBuilder.build();
    }


    /**
     * 相似性搜索
     *
     * @param query 查询字符串
     * @param topK  返回的相似文档数量
     * @return
     */
    public List<Document> similaritySearch(String query, int topK) {
        Assert.hasText(query, "查询不能为空");

        logger.info("执行相似性搜索: query={}, businessType={}, topK={}", query, topK);

        // 创建业务类型过滤器
        SearchRequest searchRequest = SearchRequest.builder().query(query).topK(topK).build();
        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        return documents;
    }

}