package com.drafire.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class GraphMetrics {

    private final MeterRegistry registry;

    public GraphMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ==================== 请求量埋点 ====================

    public void recordRequest(String mode, String intent, String status) {
        Counter.builder("chat_requests_total")
                .description("Total chat requests")
                .tag("mode", mode)
                .tag("intent", intent)
                .tag("status", status)
                .register(registry)
                .increment();
    }

    // ==================== 耗时埋点 ====================

    public Timer.Sample startRequestTimer() {
        return Timer.start();
    }

    public void stopRequestTimer(Timer.Sample sample, String mode, String intent) {
        Timer timer = Timer.builder("chat_request_duration_seconds")
                .description("Chat request duration")
                .tag("mode", mode)
                .tag("intent", intent)
                .register(registry);
        sample.stop(timer);
    }

    public Timer.Sample startLlmTimer() {
        return Timer.start();
    }

    public void stopLlmTimer(Timer.Sample sample, String node, String model) {
        Timer timer = Timer.builder("chat_llm_call_duration_seconds")
                .description("LLM call duration")
                .tag("node", node)
                .tag("model", model)
                .register(registry);
        sample.stop(timer);
    }

    // ==================== Token 消耗埋点 ====================

    public void recordTokenUsage(String mode, String type, long tokens) {
        Counter.builder("chat_token_usage_total")
                .description("Total token usage")
                .tag("mode", mode)
                .tag("type", type)
                .register(registry)
                .increment(tokens);
    }

    // ==================== 意图分类埋点 ====================

    public void recordIntent(String intent) {
        Counter.builder("graph.intent.classified")
                .tag("intent", intent)
                .description("Intent classification count")
                .register(registry)
                .increment();
    }

    // ==================== 安全治理埋点 ====================

    public void recordInputGuarded(String reason) {
        Counter.builder("chat_input_guarded_total")
                .description("Total input guarded requests")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    // ==================== 错误埋点 ====================

    public void recordError(String mode, String errorType) {
        Counter.builder("chat_errors_total")
                .description("Total chat errors")
                .tag("mode", mode)
                .tag("error_type", errorType)
                .register(registry)
                .increment();
    }
}