package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.drafire.config.GraphMetrics;
import com.drafire.interceptor.ResponseGuard;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

public class ResponseGeneratorNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGeneratorNode.class);

    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;
    private final GraphMetrics graphMetrics;

    public ResponseGeneratorNode(ChatClient chatClient, ResponseGuard responseGuard, GraphMetrics graphMetrics) {
        this.responseGuard = responseGuard;
        this.graphMetrics = graphMetrics;
        this.chatClient = chatClient.mutate()
                .defaultSystem("""
                    你是 Funnair 航空公司的客服助手。请根据工具执行结果，
                    用友好、专业的中文回复用户。如果是取消订单的确认请求，
                    引导用户明确回复"确认取消"或"不取消"。""")
                .build();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String intent = state.value("intent", "GENERAL");
        String userInput = state.value("userInput", "");
        String toolResult = state.value("toolResult", "");
        logger.info("[响应生成] intent={}, toolResult长度={}", intent, toolResult != null ? toolResult.length() : 0);

        Timer.Sample llmSample = graphMetrics.startLlmTimer();

        if ("GENERAL".equals(intent)) {
            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, state.value("chatId", "")))
                    .user(userInput)
                    .stream()
                    .chatResponse()
                    .collectList()
                    .map(chatResponses -> {
                        StringBuilder contentBuilder = new StringBuilder();
                        long totalPromptTokens = 0;
                        long totalCompletionTokens = 0;

                        for (ChatResponse response : chatResponses) {
                            if (response != null && response.getResult() != null) {
                                contentBuilder.append(response.getResult().getOutput().getText());
                            }
                            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                                totalPromptTokens += response.getMetadata().getUsage().getPromptTokens();
                                totalCompletionTokens += response.getMetadata().getUsage().getCompletionTokens();
                            }
                        }

                        String reply = contentBuilder.toString();
                        String sanitized = responseGuard.sanitize(reply);
                        logValidation(intent, sanitized);
                        graphMetrics.stopLlmTimer(llmSample, "generate_response", "qwen3-max");

                        if (totalPromptTokens > 0 || totalCompletionTokens > 0) {
                            graphMetrics.recordTokenUsage("response_generation", "prompt", totalPromptTokens);
                            graphMetrics.recordTokenUsage("response_generation", "completion", totalCompletionTokens);
                            logger.info("[Token统计] 响应生成: prompt={}, completion={}", totalPromptTokens, totalCompletionTokens);
                        }

                        return Map.<String, Object>of("reply", sanitized != null ? sanitized : "");
                    })
                    .toFuture();
        } else {
            return chatClient.prompt()
                    .advisors(advisorSpec -> advisorSpec.param(CONVERSATION_ID, state.value("chatId", "")))
                    .user(userSpec -> userSpec.text("""
                        用户原始输入: {input}
                        工具执行结果: {result}

                        请生成自然语言回复:""")
                        .param("input", userInput)
                        .param("result", toolResult))
                    .stream()
                    .chatResponse()
                    .collectList()
                    .map(chatResponses -> {
                        StringBuilder contentBuilder = new StringBuilder();
                        long totalPromptTokens = 0;
                        long totalCompletionTokens = 0;

                        for (ChatResponse response : chatResponses) {
                            if (response != null && response.getResult() != null) {
                                contentBuilder.append(response.getResult().getOutput().getText());
                            }
                            if (response != null && response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                                totalPromptTokens += response.getMetadata().getUsage().getPromptTokens();
                                totalCompletionTokens += response.getMetadata().getUsage().getCompletionTokens();
                            }
                        }

                        String reply = contentBuilder.toString();
                        String sanitized = responseGuard.sanitize(reply);
                        logValidation(intent, sanitized);
                        graphMetrics.stopLlmTimer(llmSample, "generate_response", "qwen3-max");

                        if (totalPromptTokens > 0 || totalCompletionTokens > 0) {
                            graphMetrics.recordTokenUsage("response_generation", "prompt", totalPromptTokens);
                            graphMetrics.recordTokenUsage("response_generation", "completion", totalCompletionTokens);
                            logger.info("[Token统计] 响应生成: prompt={}, completion={}", totalPromptTokens, totalCompletionTokens);
                        }

                        return Map.<String, Object>of("reply", sanitized != null ? sanitized : "");
                    })
                    .toFuture();
        }
    }

    private void logValidation(String intent, String response) {
        if (response == null) return;
        var validation = responseGuard.validate(response);
        if (!validation.checkValid()) {
            logger.warn("[输出治理] intent={}, violations={}", intent, validation.violations());
        }
    }
}