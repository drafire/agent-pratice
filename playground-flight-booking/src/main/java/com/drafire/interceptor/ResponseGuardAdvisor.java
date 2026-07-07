package com.drafire.interceptor;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;

public class ResponseGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGuardAdvisor.class);

    private static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final ResponseGuard responseGuard;

    private final int order;

    public ResponseGuardAdvisor(ResponseGuard responseGuard) {
        this(responseGuard, DEFAULT_ORDER);
    }

    public ResponseGuardAdvisor(ResponseGuard responseGuard, int order) {
        this.responseGuard = responseGuard;
        this.order = order;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);
        return guardResponse(response);
    }

    /**
     * 过滤流式响应中的敏感字样
     *
     **/
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return streamAdvisorChain.nextStream(chatClientRequest)
                .filter(this::shouldForward)  // 过滤掉非工具调用响应
                .map(this::guardResponse);
    }

    /**
    * 如果不是工具调用，就直接过滤掉这个响应
     * @param response
     * @return
     */
    private boolean shouldForward(ChatClientResponse response) {
        if (response.chatResponse() == null) {
            return true;
        }
        for (Generation generation : response.chatResponse().getResults()) {
            AssistantMessage assistantMessage = generation.getOutput();
            if (assistantMessage.hasToolCalls()) {
                logger.debug("Filtered tool call response: {}", assistantMessage.getToolCalls());
                return false;
            }
        }
        return true;
    }

    /**
     * 把响应过滤掉敏感字样，重新组装一次
     * @param response
     * @return
     */
    private ChatClientResponse guardResponse(ChatClientResponse response) {
        if (response.chatResponse() == null) {
            return response;
        }

        List<Generation> guardedGenerations = response.chatResponse().getResults().stream()
                .map(this::guardGeneration)
                .toList();

        ChatResponse guardedChatResponse = ChatResponse.builder()
                .from(response.chatResponse())
                .generations(guardedGenerations)
                .build();

        return ChatClientResponse.builder()
                .chatResponse(guardedChatResponse)
                .context(Map.copyOf(response.context()))
                .build();
    }

    /**
      过滤敏感字样
     * @param generation
     * @return
     */
    private Generation guardGeneration(Generation generation) {
        AssistantMessage assistantMessage = generation.getOutput();
        String originalText = assistantMessage.getText();
        String guardedText = responseGuard.sanitize(originalText);
        if (!guardedText.equals(originalText)) {
            AssistantMessage guardedMessage = AssistantMessage.builder()
                    .content(guardedText)
                    .properties(assistantMessage.getMetadata())
                    .media(assistantMessage.getMedia())
                    .toolCalls(assistantMessage.getToolCalls())
                    .build();
            return new Generation(guardedMessage, generation.getMetadata());
        }
        return generation;
    }
}