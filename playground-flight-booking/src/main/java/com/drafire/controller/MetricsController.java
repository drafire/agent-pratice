package com.drafire.controller;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MeterRegistry registry;
    private final AtomicLong lastTokenPrompt = new AtomicLong(0);
    private final AtomicLong lastTokenCompletion = new AtomicLong(0);
    private final List<Long> promptHistory = Collections.synchronizedList(new LinkedList<>());
    private final List<Long> completionHistory = Collections.synchronizedList(new LinkedList<>());
    private final List<Double> intentLatencyHistory = Collections.synchronizedList(new LinkedList<>());
    private final List<Double> extractLatencyHistory = Collections.synchronizedList(new LinkedList<>());
    private final List<Double> generateLatencyHistory = Collections.synchronizedList(new LinkedList<>());

    public MetricsController(MeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("requests", buildRequestMetrics());
        result.put("tokens", buildTokenMetrics());
        result.put("latency", buildLatencyMetrics());
        result.put("errors", buildErrorMetrics());
        result.put("intents", buildIntentMetrics());
        result.put("requestsByIntent", buildRequestsByIntent());
        result.put("inputGuarded", getInputGuardedTotal());
        result.put("rawMetrics", buildRawMetrics());

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> buildRequestMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long total = getCounterValue("chat.requests.total");
        long accepted = getCounterValue("chat.requests.total", "status", "accepted");
        long blocked = getCounterValue("chat.requests.total", "status", "blocked");
        metrics.put("total", total);
        metrics.put("accepted", accepted);
        metrics.put("blocked", blocked);
        return metrics;
    }

    private Map<String, Object> buildTokenMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long prompt = getCounterValue("chat.token.usage.total", "type", "prompt");
        long completion = getCounterValue("chat.token.usage.total", "type", "completion");

        long promptRate = Math.max(0, prompt - lastTokenPrompt.get());
        long completionRate = Math.max(0, completion - lastTokenCompletion.get());
        lastTokenPrompt.set(prompt);
        lastTokenCompletion.set(completion);

        promptHistory.add(promptRate);
        completionHistory.add(completionRate);
        if (promptHistory.size() > 20) promptHistory.remove(0);
        if (completionHistory.size() > 20) completionHistory.remove(0);

        metrics.put("prompt", prompt);
        metrics.put("completion", completion);
        metrics.put("total", prompt + completion);
        metrics.put("promptRate", promptRate);
        metrics.put("completionRate", completionRate);
        return metrics;
    }

    private Map<String, Object> buildLatencyMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        double intentAvg = getTimerAvg("chat.llm.call.duration.seconds", "node", "classify_intent");
        double extractAvg = getTimerAvg("chat.llm.call.duration.seconds", "node", "extract_params");
        double generateAvg = getTimerAvg("chat.llm.call.duration.seconds", "node", "generate_response");

        intentLatencyHistory.add(intentAvg);
        extractLatencyHistory.add(extractAvg);
        generateLatencyHistory.add(generateAvg);
        if (intentLatencyHistory.size() > 20) intentLatencyHistory.remove(0);
        if (extractLatencyHistory.size() > 20) extractLatencyHistory.remove(0);
        if (generateLatencyHistory.size() > 20) generateLatencyHistory.remove(0);

        long totalCalls = (long) getTimerCount("chat.llm.call.duration.seconds");

        double overallAvg = 0;
        if (intentAvg > 0 || extractAvg > 0 || generateAvg > 0) {
            overallAvg = (intentAvg + extractAvg + generateAvg) / 3.0;
        }

        metrics.put("intent", intentLatencyHistory.isEmpty() ? 0 : intentLatencyHistory.get(intentLatencyHistory.size() - 1));
        metrics.put("extract", extractLatencyHistory.isEmpty() ? 0 : extractLatencyHistory.get(extractLatencyHistory.size() - 1));
        metrics.put("generate", generateLatencyHistory.isEmpty() ? 0 : generateLatencyHistory.get(generateLatencyHistory.size() - 1));
        metrics.put("avg", overallAvg);
        metrics.put("count", totalCalls);
        return metrics;
    }

    private Map<String, Object> buildErrorMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        long total = getCounterValue("chat.errors.total");
        metrics.put("total", total);
        return metrics;
    }

    private Map<String, Long> buildIntentMetrics() {
        Map<String, Long> intents = new LinkedHashMap<>();
        registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("graph.intent.classified"))
                .filter(m -> m instanceof io.micrometer.core.instrument.Counter)
                .forEach(m -> {
                    String intent = m.getId().getTag("intent");
                    if (intent != null) {
                        intents.put(intent, (long) ((io.micrometer.core.instrument.Counter) m).count());
                    }
                });
        return intents;
    }

    private Map<String, Map<String, Long>> buildRequestsByIntent() {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("chat.requests.total"))
                .filter(m -> m instanceof io.micrometer.core.instrument.Counter)
                .forEach(m -> {
                    String intent = m.getId().getTag("intent");
                    String status = m.getId().getTag("status");
                    if (intent != null && status != null) {
                        result.computeIfAbsent(intent, k -> new LinkedHashMap<>());
                        long count = (long) ((io.micrometer.core.instrument.Counter) m).count();
                        result.get(intent).merge(status, count, Long::sum);
                    }
                });
        return result;
    }

    private long getInputGuardedTotal() {
        return getCounterValue("chat.input.guarded.total");
    }

    private List<Map<String, String>> buildRawMetrics() {
        List<Map<String, String>> rawMetrics = new ArrayList<>();
        registry.getMeters().forEach(meter -> {  //这里会遍历所有指标，包含jvm 等
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("name", meter.getId().getName());
            entry.put("type", meter.getId().getType().toString());

            List<String> tags = new ArrayList<>();
            meter.getId().getTags().forEach(t -> tags.add(t.getKey() + "=" + t.getValue()));
            entry.put("tags", String.join(", ", tags));

            String value = "";
            if (meter instanceof io.micrometer.core.instrument.Counter) {
                value = String.format("%.0f", ((io.micrometer.core.instrument.Counter) meter).count());
            } else if (meter instanceof io.micrometer.core.instrument.Timer) {
                io.micrometer.core.instrument.Timer timer = (io.micrometer.core.instrument.Timer) meter;
                value = String.format("%.3fs (count=%d)", (timer.totalTime(java.util.concurrent.TimeUnit.SECONDS) / Math.max(1, timer.count())), (long) timer.count());
            } else if (meter instanceof io.micrometer.core.instrument.Gauge) {
                value = String.format("%.2f", ((io.micrometer.core.instrument.Gauge) meter).value());
            }
            entry.put("value", value);

            rawMetrics.add(entry);
        });
        return rawMetrics;
    }

    private long getCounterValue(String name) {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(name))
                .filter(m -> m instanceof io.micrometer.core.instrument.Counter)
                .mapToLong(m -> (long) ((io.micrometer.core.instrument.Counter) m).count())
                .sum();
    }

    private long getCounterValue(String name, String tagKey, String tagValue) {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(name))
                .filter(m -> m instanceof io.micrometer.core.instrument.Counter)
                .filter(m -> tagValue.equals(m.getId().getTag(tagKey)))
                .mapToLong(m -> (long) ((io.micrometer.core.instrument.Counter) m).count())
                .sum();
    }

    private double getTimerAvg(String name, String tagKey, String tagValue) {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(name))
                .filter(m -> m instanceof io.micrometer.core.instrument.Timer)
                .filter(m -> tagValue.equals(m.getId().getTag(tagKey)))
                .mapToDouble(m -> ((io.micrometer.core.instrument.Timer) m).mean(java.util.concurrent.TimeUnit.SECONDS))
                .average()
                .orElse(0);
    }

    private double getTimerCount(String name) {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals(name))
                .filter(m -> m instanceof io.micrometer.core.instrument.Timer)
                .mapToDouble(m -> ((io.micrometer.core.instrument.Timer) m).count())
                .sum();
    }
}