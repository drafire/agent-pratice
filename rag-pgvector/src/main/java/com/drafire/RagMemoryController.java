package com.drafire;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.*;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/rag/memory")
public class RagMemoryController {
    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor messageChatMemoryAdvisor;
    private final RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    public RagMemoryController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory,
                               VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.retrievalAugmentationAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .similarityThreshold(0.5)
                        .build())
                .build();
    }

    @PostMapping("/{chatId}")
    public String chatWithDocument(@RequestBody String prompt, @PathVariable("chatId") String conversationId) {
        return chatClient.prompt(prompt).advisors(messageChatMemoryAdvisor, retrievalAugmentationAdvisor)
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, conversationId))
                .user(prompt)
                .call().content();
    }
}
