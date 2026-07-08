package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ParameterExtractorNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ParameterExtractorNode.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    public ParameterExtractorNode(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String intent = state.value("intent", "GENERAL");
        String userInput = state.value("userInput", "");
        logger.info("[参数提取] 意图: {}, 输入: {}", intent, userInput);

        String schema = switch (intent) {
            case "BOOKING_DETAILS", "CANCEL_BOOKING" -> """
                    {
                      "bookingNumber": "预定编号（如 BKG-001）",
                      "customerName": "乘客姓名"
                    }""";
            case "CHANGE_BOOKING" -> """
                    {
                      "bookingNumber": "预定编号",
                      "customerName": "乘客姓名",
                      "from": "新出发地（如 北京）",
                      "to": "新目的地（如 上海）",
                      "date": "新日期（格式 yyyy-MM-dd）"
                    }""";
            case "SEARCH_FLIGHTS" -> """
                    {
                      "from": "出发城市",
                      "to": "目的城市",
                      "date": "出发日期（格式 yyyy-MM-dd）"
                    }""";
            case "WEATHER" -> """
                    {
                      "city": "城市名称",
                      "days": "查询天数（0=当天实时天气，1-7=未来N天预报）"
                    }""";
            default -> null;
        };

        if (schema == null) {
            return CompletableFuture.completedFuture(Map.of());
        }

        return chatClient.prompt()
                .user(userSpec -> userSpec.text("""
                    从用户输入中提取结构化参数。只返回 JSON，不要返回其他内容。

                    目标 JSON 格式:
                    {schema}

                    用户输入: {input}

                    JSON:""")
                    .param("schema", schema)
                    .param("input", userInput))
                .stream()
                .content()
                .collectList()
                .map(list -> {
                    try {
                        String json = extractJson(String.join("", list));
                        JsonNode root = objectMapper.readTree(json);

                        Map<String, Object> result = new HashMap<>();
                        if (root.has("bookingNumber")) result.put("bookingNumber", root.get("bookingNumber").asText());
                        if (root.has("customerName")) result.put("customerName", root.get("customerName").asText());
                        if (root.has("from")) result.put("from", root.get("from").asText());
                        if (root.has("to")) result.put("to", root.get("to").asText());
                        if (root.has("date")) result.put("date", root.get("date").asText());
                        if (root.has("city")) result.put("weatherCity", root.get("city").asText());
                        if (root.has("days")) result.put("weatherDays", root.get("days").asInt());

                        logger.info("[参数提取] 结果: {}", result);
                        return result;
                    } catch (Exception e) {
                        logger.error("[参数提取] 失败: {}", e.getMessage());
                        return Map.<String, Object>of();
                    }
                })
                .toFuture();
    }

    private String extractJson(String raw) {
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}