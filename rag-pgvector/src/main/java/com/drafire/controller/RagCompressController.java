package com.drafire.controller;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rag/compression")
public class RagCompressController {
    private final ChatClient chatClient;

    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;

    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    public RagCompressController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
                                 VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        VectorStoreDocumentRetriever vectorStoreDocumentRetriever = VectorStoreDocumentRetriever.builder().vectorStore(vectorStore)
                .similarityThreshold(0.5).build();
        CompressionQueryTransformer compressionQueryTransformer = CompressionQueryTransformer.builder().chatClientBuilder(chatClientBuilder.build().mutate()).build();

        this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(vectorStoreDocumentRetriever)
                .queryTransformers(compressionQueryTransformer)
                .build();
    }

    @PostMapping("/{chatId}")
    public String rag(@RequestBody String prompt, @PathVariable("chatId") String conversationId) {
        return chatClient.prompt().user(prompt).advisors(messageChatMemoryAdvisor, retrievalAugmentationAdvisor)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();

    }
}
