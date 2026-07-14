package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.drafire.config.GraphMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IntentClassifierNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(IntentClassifierNode.class);

    private final ChatClient chatClient;
    private final GraphMetrics graphMetrics;

    public IntentClassifierNode(ChatClient chatClient, GraphMetrics graphMetrics) {
        this.chatClient = chatClient;
        this.graphMetrics = graphMetrics;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String userInput = state.value("userInput", "");
        logger.info("[意图分类] 用户输入: {}", userInput);

        Timer.Sample llmSample = graphMetrics.startLlmTimer();

        return chatClient.prompt()
                .user(userSpec -> userSpec.text("""
                        你是一个意图分类器。根据用户输入，只返回以下类别之一（不要返回其他内容）：
                        
                        - BOOKING_DETAILS: 查询订单详情
                        - CHANGE_BOOKING: 修改/改签订单
                        - CANCEL_BOOKING: 取消订单
                        - SEARCH_FLIGHTS: 搜索航班
                        - WEATHER: 查询天气
                        - GENERAL: 一般对话/闲聊
                        
                        用户输入: {input}
                        
                        意图:""").param("input", userInput))
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

                    String intent = contentBuilder.toString().trim().toUpperCase();
                    logger.info("[意图分类] 识别结果: {}", intent);
                    graphMetrics.recordIntent(intent);
                    graphMetrics.stopLlmTimer(llmSample, "classify_intent", "qwen3-max");

                    if (totalPromptTokens > 0 || totalCompletionTokens > 0) {
                        graphMetrics.recordTokenUsage("intent_classification", "prompt", totalPromptTokens);
                        graphMetrics.recordTokenUsage("intent_classification", "completion", totalCompletionTokens);
                        logger.info("[Token统计] intent分类: prompt={}, completion={}", totalPromptTokens, totalCompletionTokens);
                    }

                    return Map.<String, Object>of("intent", intent);
                })
                .toFuture();
    }
}