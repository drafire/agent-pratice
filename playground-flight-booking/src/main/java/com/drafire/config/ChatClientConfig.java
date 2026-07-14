package com.drafire.config;

import com.drafire.interceptor.ResponseGuard;
import com.drafire.interceptor.ResponseGuardAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, ResponseGuard responseGuard) {
        return builder
                .defaultAdvisors(
                        new ResponseGuardAdvisor(responseGuard),
                        new SimpleLoggerAdvisor(),
                        PromptChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}

