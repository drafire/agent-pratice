package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.drafire.interceptor.ResponseGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ResponseGeneratorNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGeneratorNode.class);

    private final ChatClient chatClient;
    private final ResponseGuard responseGuard;

    public ResponseGeneratorNode(ChatClient.Builder builder, ResponseGuard responseGuard) {
        this.responseGuard = responseGuard;
        this.chatClient = builder
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

        if ("GENERAL".equals(intent)) {
            return chatClient.prompt()
                    .user(userInput)
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> {
                        String reply = String.join("", list);
                        String sanitized = responseGuard.sanitize(reply);
                        logValidation(intent, sanitized);
                        return Map.<String, Object>of("reply", sanitized != null ? sanitized : "");
                    })
                    .toFuture();
        } else {
            return chatClient.prompt()
                    .user(userSpec -> userSpec.text("""
                        用户原始输入: {input}
                        工具执行结果: {result}

                        请生成自然语言回复:""")
                        .param("input", userInput)
                        .param("result", toolResult))
                    .stream()
                    .content()
                    .collectList()
                    .map(list -> {
                        String reply = String.join("", list);
                        String sanitized = responseGuard.sanitize(reply);
                        logValidation(intent, sanitized);
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