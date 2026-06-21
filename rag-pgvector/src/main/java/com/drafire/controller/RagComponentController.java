package com.drafire.controller;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankProperties;
import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.rag.postretrieval.DashScopeRerankPostProcessor;
import com.alibaba.cloud.ai.rag.preretrieval.transformation.HyDeTransformer;
import com.alibaba.cloud.ai.rag.retrieval.search.HyDeRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/rag/component")
public class RagComponentController {
    private final HyDeRetriever hyDeRetriever;

    private final HyDeTransformer hyDeTransformer;

    private final ChatClient.Builder chatClientBuilder;

    private final RerankModel rerankModel;

    private final ChatClient chatClient;

    public RagComponentController(ChatClient.Builder chatClientBuilder, RerankModel rerankModel
            , VectorStore vectorStore) {
        this.hyDeTransformer = HyDeTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
        this.hyDeRetriever = HyDeRetriever.builder()
                .hyDeTransformer(hyDeTransformer)
                .vectorStore(vectorStore)
                .build();
        this.chatClientBuilder = chatClientBuilder;
        this.rerankModel = rerankModel;
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/retrieval/hyde")
    public List<Document> retrievalHyde(String message) {
        return this.hyDeRetriever.retrieve(Query.builder()
                .text(message)
                .build());
    }

    /**
     * 基于假设文档的RAG查询组件，包括Query改写、hyde假设文档生成、hyde假设文档检索、rerank后处理。可以提高
     * @param message
     * @return
     */
    @GetMapping("/query")
    public String query(String message) {
        RetrievalAugmentationAdvisor retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(List.of(RewriteQueryTransformer.builder()  //Query改写
                        .chatClientBuilder(this.chatClientBuilder)
                        .build(),
                        hyDeTransformer))  //hyde假设文档生成
                .documentRetriever(this.hyDeRetriever)   //hyde假设文档检索
                .documentPostProcessors(List.of(DashScopeRerankPostProcessor.builder()   //rerank后处理
                        .rerankModel(rerankModel)  //rerank模型，不可以为空
                        .rerankOptions(DashScopeRerankOptions.builder().build())  //rerank选项，不可以为空
                        .build()))
                .build();
        return chatClient.prompt()
                .advisors(retrievalAugmentationAdvisor)
                .user(message).call().content();
    }
}
