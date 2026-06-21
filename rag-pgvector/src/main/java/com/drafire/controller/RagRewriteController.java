package com.drafire.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rag/rewrite")
public class RagRewriteController {
    private final ChatClient chatClient;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    public RagRewriteController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        VectorStoreDocumentRetriever storeDocumentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(vectorStore).similarityThreshold(0.5).build();
        RewriteQueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder.build().mutate())
                .targetSearchSystem("vector store")
                .build();


        this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(queryTransformer)
                .documentRetriever(storeDocumentRetriever)
                .build();
    }

    @PostMapping("/query")
    public String rag(@RequestBody String prompt) {
        return chatClient.prompt().advisors(retrievalAugmentationAdvisor).user(prompt).call().content();
    }
}
