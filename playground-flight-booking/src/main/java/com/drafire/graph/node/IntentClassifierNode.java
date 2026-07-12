package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class IntentClassifierNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(IntentClassifierNode.class);

    private final ChatClient chatClient;

    public IntentClassifierNode(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder.build();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String userInput = state.value("userInput", "");
        logger.info("[意图分类] 用户输入: {}", userInput);

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
                .content()
                .collectList()
                .map(list -> {
                    String intent = String.join("", list).trim().toUpperCase();
                    logger.info("[意图分类] 识别结果: {}", intent);
                    return Map.<String, Object>of("intent", intent);
                })
                .toFuture();
    }
}