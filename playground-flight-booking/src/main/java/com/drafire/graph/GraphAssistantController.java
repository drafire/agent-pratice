package com.drafire.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.drafire.exception.BusinessException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

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

        // ═════════════════════════════════════════════════════════════════
        // 关键修复：直接操作 Flux 链，不使用 subscribe + sink 模式
        //
        // 核心注意事项：
        // - L246 (aroundChat() 的 catch 块) 永远不会被调用！
        //   因为 Controller 返回的是 Flux（懒执行），pjp.proceed() 只是创建 Flux 对象不抛异常
        // - 真正的熔断计数在 ChatGlobalAspect.attachGraphHooks() 的 doOnError (L364)
        // - 这里必须确保：非预期异常以 Flux.error(error) 形式传播，不能吞掉！
        // ═════════════════════════════════════════════════════════════════
        return compiledGraph.stream(input, config)
                .doOnError(error -> logger.error("🔴 [Graph] 原始流发出 error 信号: {}", error.getMessage()))
                .filter(nodeOutput -> "generate_response".equals(nodeOutput.node()))
                .handle((NodeOutput nodeOutput, SynchronousSink<ServerSentEvent<String>> sink) -> {
                    Object reply = nodeOutput.state().value("reply", "");
                    if (reply != null && !reply.toString().isEmpty()) {
                        sink.next(ServerSentEvent.builder(reply.toString()).build());
                    }
                })
                .doOnError(error -> logger.error("🔴 [Graph] filter/handle 后仍有 error 信号: {}", error.getMessage()))
                .onErrorResume(error -> {
                    // ─── 区分处理两类异常 ───
                    if (error instanceof BusinessException) {
                        // ① 预期业务错误：返回友好消息，流正常完成（不增加熔断失败计数）
                        BusinessException be = (BusinessException) error;
                        String reply = "抱歉，" + be.getErrorCode() + "：" + be.getMessage();
                        logger.warn("🟡 [Graph] 业务异常，返回友好消息（不触发熔断）: {}", reply);
                        return Flux.just(ServerSentEvent.builder(reply).build());
                    }
                    // ② 非预期异常：关键！不能在这里吞掉 error 信号
                    //   → Flux.error(error) 会继续传播到 Aspect 的 doOnError
                    //   → circuitBreaker.onError() 才会被调用，熔断器才会打开
                    logger.error("🔴 [Graph] 系统异常，传播 error 信号触发熔断: {}", error.getMessage());
                    return Flux.error(error);
                })
                .doOnError(error -> logger.error("🔴 [Graph] onErrorResume 后仍有 error 信号（预期行为）: {}", error.getMessage()))
                .doOnComplete(() -> logger.info("🟢 [Graph] 流正常完成"))
                .doOnCancel(() -> logger.info("🟡 [Graph] 客户端断开连接"));
    }
}