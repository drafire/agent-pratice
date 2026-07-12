package com.drafire.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphAssistantController {

    private static final Logger logger = LoggerFactory.getLogger(GraphAssistantController.class);

    private final CompiledGraph compiledGraph;
    private final Tracer tracer;

    public GraphAssistantController(StateGraph flightAgentStateGraph, Tracer tracer) throws GraphStateException {
        this.compiledGraph = flightAgentStateGraph.compile();
        this.tracer = tracer;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestParam String chatId,
            @RequestParam String userMessage) {

        Map<String, Object> input = new HashMap<>();
        input.put("userInput", userMessage);
        input.put("chatId", chatId);

        // 从当前 span 中提取 traceId/spanId（WebFlux 中它们存在于 Reactor Context，不在 MDC 中）
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            MDC.put("traceId", currentSpan.context().traceId());
            MDC.put("spanId", currentSpan.context().spanId());
        }
        // 补充 chatId 和 mode（Aspect 中已设置，但此处确保不遗漏）
        MDC.put("chatId", chatId);
        // 捕获完整 MDC（含 traceId、spanId、chatId、mode），注入 state 供节点恢复
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc != null && !mdc.isEmpty()) {
            input.put("_mdc", mdc);
        }
        RunnableConfig config = RunnableConfig.builder().threadId(chatId).build();

        Flux<NodeOutput> stream = compiledGraph.stream(input, config);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        stream.subscribe(
                nodeOutput -> {
                    if ("generate_response".equals(nodeOutput.node())) {
                        Object reply = nodeOutput.state().value("reply", "");
                        if (reply != null && !reply.toString().isEmpty()) {
                            sink.tryEmitNext(ServerSentEvent.builder(reply.toString()).build());
                        }
                    }
                },
                error -> {
                    logger.error("Graph 执行失败", error);
                    sink.tryEmitNext(ServerSentEvent.builder("抱歉，处理请求时出错了").build());
                    sink.tryEmitComplete();
                },
                () -> sink.tryEmitComplete()
        );

        return sink.asFlux()
                .doOnCancel(() -> logger.info("客户端断开连接"))
                .doOnError(e -> logger.error("SSE 流错误", e));
    }
}