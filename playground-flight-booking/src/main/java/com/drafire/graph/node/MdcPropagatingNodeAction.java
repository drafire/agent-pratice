package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unchecked")
public class MdcPropagatingNodeAction implements AsyncNodeAction {

    private final AsyncNodeAction delegate;
    private final MeterRegistry registry;
    private final String nodeName;

    public MdcPropagatingNodeAction(AsyncNodeAction delegate, MeterRegistry registry, String nodeName) {
        this.delegate = delegate;
        this.registry = registry;
        this.nodeName = nodeName;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        // 从 state 中恢复 MDC 上下文（由 GraphAssistantController 在调用 stream() 前注入）
        Object mdcObj = state.value("_mdc", null);
        
        Map<String, String> mdc = extractMdc(mdcObj);
        if (mdc != null && !mdc.isEmpty()) {
            MDC.setContextMap(mdc);
        } else {
            // 如果 _mdc 不存在，说明 Controller 没有注入，需要手动从 state 提取
            String chatId = state.value("chatId", "");
            if (!chatId.isEmpty()) {
                MDC.put("chatId", chatId);
            }
        }
        // 补充 chatId（独立放入 state，确保不遗漏）
        String chatId = state.value("chatId", "");
        if (!chatId.isEmpty()) {
            MDC.put("chatId", chatId);
        }

        Timer.Sample sample = Timer.start();
        return delegate.apply(state)
                .whenComplete((result, error) -> {
                    sample.stop(Timer.builder("graph.node.execution.time")
                            .description("Graph node execution time")
                            .tag("node", nodeName)
                            .register(registry));
                    MDC.clear();
                });
    }
    
    /**
     * 从 state.value() 返回值中提取 Map（可能是 Optional 包装的）
     */
    private Map<String, String> extractMdc(Object mdcObj) {
        if (mdcObj instanceof Optional) {
            Optional<?> opt = (Optional<?>) mdcObj;
            if (opt.isPresent() && opt.get() instanceof Map) {
                return (Map<String, String>) opt.get();
            }
        } else if (mdcObj instanceof Map) {
            return (Map<String, String>) mdcObj;
        }
        return null;
    }
}