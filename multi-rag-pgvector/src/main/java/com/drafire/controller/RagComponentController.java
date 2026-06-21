package com.drafire.controller;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.rag.postretrieval.DashScopeRerankPostProcessor;
import com.alibaba.cloud.ai.rag.preretrieval.transformation.HyDeTransformer;
import com.alibaba.cloud.ai.rag.retrieval.search.HyDeRetriever;
import com.drafire.config.KnowledgeBaseVectorStoreManager;
import com.drafire.service.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag/component")
public class RagComponentController {

    private static final Logger logger = LoggerFactory.getLogger(RagComponentController.class);

    private final HyDeTransformer hyDeTransformer;

    private final ChatClient.Builder chatClientBuilder;

    private final RerankModel rerankModel;

    private final ChatClient chatClient;

    private final KnowledgeBaseVectorStoreManager storeManager;

    private final KnowledgeBaseService knowledgeBaseService;

    public RagComponentController(ChatClient.Builder chatClientBuilder, RerankModel rerankModel,
                                  KnowledgeBaseVectorStoreManager storeManager,
                                  KnowledgeBaseService knowledgeBaseService) {
        this.hyDeTransformer = HyDeTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
        this.chatClientBuilder = chatClientBuilder;
        this.rerankModel = rerankModel;
        this.chatClient = chatClientBuilder.build();
        this.storeManager = storeManager;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/retrieval/hyde")
    public List<Document> retrievalHyde(@RequestParam String message,
                                        @RequestParam(defaultValue = "product") String scope) {
        return buildHyDeRetriever(scope).retrieve(Query.builder()
                .text(message)
                .build());
    }

    @GetMapping("/query")
    public String query(@RequestParam String message,
                        @RequestParam(defaultValue = "product") String scope) {
        HyDeRetriever scopeRetriever = buildHyDeRetriever(scope);
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(List.of(RewriteQueryTransformer.builder()
                                .chatClientBuilder(this.chatClientBuilder)
                                .build(),
                        hyDeTransformer))
                .documentRetriever(scopeRetriever)
                .documentPostProcessors(List.of(DashScopeRerankPostProcessor.builder()
                        .rerankModel(rerankModel)
                        .rerankOptions(DashScopeRerankOptions.builder().withModel("qwen3-rerank").build())
                        .build()))
                .build();
        return chatClient.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(message).call().content();
    }

    private String safeCall(ChatClient.ChatClientRequestSpec prompt) {
        try {
            return prompt.call().content();
        } catch (NonTransientAiException e) {
            logger.warn("RAG 调用失败: {}", e.getMessage());
            return "未找到相关文档，请尝试换个问法";
        }
    }

    private HyDeRetriever buildHyDeRetriever(String scope) {
        PgVectorStore vectorStore = storeManager.getStore(scope);
        return HyDeRetriever.builder()
                .hyDeTransformer(hyDeTransformer)
                .vectorStore(vectorStore)
                .build();
    }

    @GetMapping("/multi-query")
    public String multiQuery(@RequestParam String message,
                             @RequestParam(defaultValue = "product,tech,faq") List<String> scopes) {
        if (knowledgeBaseService.multiRecall(message, scopes).isEmpty()) {
            return "未在任何知识库中找到相关文档，请先导入数据";
        }

        DocumentRetriever multiRetriever = query -> {
            List<Document> docs = knowledgeBaseService.multiRecall(query.text(), scopes);
            if (docs.isEmpty()) {
                logger.warn("改写后查询无结果，回退到原始查询");
                docs = knowledgeBaseService.multiRecall(message, scopes);
            }
            return docs;
        };

        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(List.of(RewriteQueryTransformer.builder()
                                .chatClientBuilder(this.chatClientBuilder)
                                .build(),
                        hyDeTransformer))
                .documentRetriever(multiRetriever)
                .documentPostProcessors(List.of(DashScopeRerankPostProcessor.builder()
                        .rerankModel(rerankModel)
                        .rerankOptions(DashScopeRerankOptions.builder().withModel("qwen3-rerank").build())
                        .build()))
                .build();
        return chatClient.prompt()
                .advisors(advisor)
                .user(message).call().content();
    }
}