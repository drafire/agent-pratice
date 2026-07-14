package com.drafire.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class GraphMetrics {

    private final MeterRegistry registry;
    private final Counter totalRequests;
    private final Counter successRequests;
    private final Counter failedRequests;
    private final Counter intentClassified;

    private final Timer graphExecutionTimer;
    private final Timer llmCallTimer;

    public GraphMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.totalRequests = Counter.builder("graph.requests.total")
                .description("Total graph requests")
                .register(registry);

        this.successRequests = Counter.builder("graph.requests.success")
                .description("Successful graph requests")
                .register(registry);

        this.failedRequests = Counter.builder("graph.requests.failed")
                .description("Failed graph requests")
                .register(registry);

        this.intentClassified = Counter.builder("graph.intent.classified")
                .description("Intent classification count")
                .register(registry);

        this.graphExecutionTimer = Timer.builder("graph.execution.time")
                .description("Graph execution time")
                .register(registry);

        this.llmCallTimer = Timer.builder("graph.llm.call.time")
                .description("LLM call time")
                .register(registry);
    }

    public void recordRequest() {
        totalRequests.increment();
    }

    public void recordSuccess() {
        successRequests.increment();
    }

    public void recordFailure() {
        failedRequests.increment();
    }

    public void recordIntent(String intent) {
        Counter.builder("graph.intent.classified")
                .tag("intent", intent)
                .description("Intent classification count")
                .register(registry)
                .increment();
    }

    public Timer.Sample startGraphTimer() {
        return Timer.start();
    }

    public void stopGraphTimer(Timer.Sample sample) {
        sample.stop(graphExecutionTimer);
    }

    public Timer.Sample startLlmTimer() {
        return Timer.start();
    }

    public void stopLlmTimer(Timer.Sample sample) {
        sample.stop(llmCallTimer);
    }
}