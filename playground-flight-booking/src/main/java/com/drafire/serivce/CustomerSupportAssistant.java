package com.drafire.serivce;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class CustomerSupportAssistant {
    private final ChatClient chatClient;

    public CustomerSupportAssistant(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory,
                                    @Value("classpath:/prompts/flight-assistant.st") Resource resource) {
        try {
            String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
            this.chatClient = builder
                    .defaultSystem(systemPrompt)
                    .defaultAdvisors(PromptChatMemoryAdvisor.builder(chatMemory).build(),
                            QuestionAnswerAdvisor.builder(vectorStore).build(),
                            new SimpleLoggerAdvisor())
                    .defaultToolNames("queryFlightBookingDetails", "modifyFlightBooking", "cancelFlightBooking", "getWeatherByCity")
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("无法加载系统提示词文件: prompts/flight-assistant.st", e);
        }
    }

    public Flux<String> chat(String chatId, String userMessage) {
        return chatClient.prompt()
                .system(promptSystemSpec -> promptSystemSpec.param("current_date", LocalDate.now().toString()))
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, chatId)
                        .param(TOP_K, 100))
                .advisors(new SimpleLoggerAdvisor())
                .user(userMessage)
                .stream().content();
    }
}