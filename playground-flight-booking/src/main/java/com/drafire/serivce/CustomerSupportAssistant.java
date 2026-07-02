package com.drafire.serivce;

import com.drafire.interceptor.ResponseGuard;
import com.drafire.interceptor.ResponseGuardAdvisor;
import com.drafire.interceptor.ToolRegistry;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.springframework.ai.chat.client.advisor.vectorstore.VectorStoreChatMemoryAdvisor.TOP_K;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@Service
public class CustomerSupportAssistant {
    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;

    public CustomerSupportAssistant(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory,
                                    @Value("classpath:/prompts/flight-assistant.st") Resource resource,
                                    ResponseGuard responseGuard, ToolRegistry toolRegistry) {
        try {
            String systemPrompt = resource.getContentAsString(StandardCharsets.UTF_8);
            this.responseGuard = responseGuard;
            this.chatClient = builder
                    .defaultSystem(systemPrompt)
                    .defaultAdvisors(
                            new ResponseGuardAdvisor(responseGuard),
                            PromptChatMemoryAdvisor.builder(chatMemory).build(),
                            //QuestionAnswerAdvisor.builder(vectorStore).build(),
                            new SimpleLoggerAdvisor())
                    .defaultToolNames(toolRegistry.toArray())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("无法加载系统提示词文件: prompts/flight-assistant.st", e);
        }
    }

    public Flux<String> chat(String chatId, String userMessage) {
        AtomicBoolean leakDetected = new AtomicBoolean(false);
        return chatClient.prompt()
                .system(promptSystemSpec -> promptSystemSpec.param("current_date", LocalDate.now().toString()))
                .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, chatId)
                        .param(TOP_K, 100))
                .user(userMessage)
                .stream().content()
                .map(chunk -> {
                    if (leakDetected.get()) {
                        return "";
                    }
                    if (responseGuard.containsPromptLeak(chunk)) {
                        leakDetected.set(true);
                    }
                    return responseGuard.sanitize(chunk);
                })
                .filter(chunk -> !chunk.isEmpty());
    }
}