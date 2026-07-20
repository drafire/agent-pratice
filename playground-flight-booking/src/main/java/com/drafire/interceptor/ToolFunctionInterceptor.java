package com.drafire.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class ToolFunctionInterceptor implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ToolFunctionInterceptor.class);

    private final ResponseRenderer responseRenderer;
    private final ToolRegistry toolRegistry;

    public ToolFunctionInterceptor(ResponseRenderer responseRenderer, ToolRegistry toolRegistry) {
        this.responseRenderer = responseRenderer;
        this.toolRegistry = toolRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof Function && toolRegistry.contains(beanName)) {
            Function<Object, Object> func = (Function<Object, Object>) bean;
            return (Function<Object, String>) input -> {
                try {
                    Object result = func.apply(input);
                    return result instanceof String s ? s : String.valueOf(result);
                } catch (Exception e) {
                    logger.error("工具执行失败: tool={}, input={}, error={}", beanName, input, e.getMessage());
                    return responseRenderer.renderError();
                }
            };
        }
        return bean;
    }

    private Throwable getRootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}