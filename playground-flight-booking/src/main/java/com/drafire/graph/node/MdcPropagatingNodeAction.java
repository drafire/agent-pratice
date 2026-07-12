package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unchecked")
public class MdcPropagatingNodeAction implements AsyncNodeAction {

    private final AsyncNodeAction delegate;

    public MdcPropagatingNodeAction(AsyncNodeAction delegate) {
        this.delegate = delegate;
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

        return delegate.apply(state)
                .whenComplete((result, error) -> MDC.clear());
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