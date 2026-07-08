package com.drafire.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Aspect
@Component
public class ChatGlobalAspect {

    private static final Logger logger = LoggerFactory.getLogger(ChatGlobalAspect.class);

    private static final String SAFE_FALLBACK = "抱歉，服务暂时不可用，请稍后再试。";

    private final ResponseGuard responseGuard;

    public ChatGlobalAspect(ResponseGuard responseGuard) {
        this.responseGuard = responseGuard;
    }

    @Pointcut("execution(* com.drafire.serivce.CustomerSupportAssistant.chat(..))")
    public void functionCallingChat() {}

    @Pointcut("execution(* com.drafire.graph.GraphAssistantController.chat(..))")
    public void graphChat() {}

    @Around("functionCallingChat()")
    public Object aroundFunctionCalling(ProceedingJoinPoint pjp) {
        return aroundChat(pjp, "FunctionCalling");
    }

    @Around("graphChat()")
    public Object aroundGraph(ProceedingJoinPoint pjp) {
        return aroundChat(pjp, "Graph");
    }

    @SuppressWarnings("unchecked")
    private Object aroundChat(ProceedingJoinPoint pjp, String mode) {
        Object[] args = pjp.getArgs();
        String chatId = (String) args[0];
        String userMessage = (String) args[1];

        logger.info("Chat 请求 [{}]: chatId={}, message={}", mode, chatId, userMessage);

        long start = System.currentTimeMillis();
        try {
            Object rawResult = pjp.proceed();

            if (rawResult instanceof Flux<?> flux) {
                if ("Graph".equals(mode)) {
                    return attachGraphHooks(chatId, start, (Flux<ServerSentEvent<String>>) flux);
                } else {
                    return attachFunctionCallingHooks(chatId, start, (Flux<String>) flux);
                }
            }

            return rawResult;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            logger.error("Chat 同步异常 [{}]: chatId={}, 耗时={}ms, error={}", mode, chatId, elapsed, e.getMessage());
            return Flux.just(SAFE_FALLBACK);
        }
    }

    private Flux<String> attachFunctionCallingHooks(String chatId, long start, Flux<String> result) {
        StringBuilder fullResponse = new StringBuilder();

        return result
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - start;
                    validateAndLog(chatId, elapsed, fullResponse.toString());
                })
                .doOnError(error -> {
                    long elapsed = System.currentTimeMillis() - start;
                    logger.error("Chat 异常: chatId={}, 耗时={}ms, error={}", chatId, elapsed, error.getMessage());
                })
                .onErrorResume(error -> Flux.just(SAFE_FALLBACK));
    }

    private Flux<ServerSentEvent<String>> attachGraphHooks(String chatId, long start,
                                                            Flux<ServerSentEvent<String>> result) {
        StringBuilder fullResponse = new StringBuilder();

        return result
                .doOnNext(event -> {
                    if (event != null && event.data() != null) {
                        fullResponse.append(event.data());
                    }
                })
                .doOnComplete(() -> {
                    long elapsed = System.currentTimeMillis() - start;
                    validateAndLog(chatId, elapsed, fullResponse.toString());
                })
                .doOnError(error -> {
                    long elapsed = System.currentTimeMillis() - start;
                    logger.error("Graph Chat 异常: chatId={}, 耗时={}ms, error={}", chatId, elapsed, error.getMessage());
                })
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .data(SAFE_FALLBACK).build()));
    }

    private void validateAndLog(String chatId, long elapsed, String response) {
        if (response != null && !response.isEmpty()) {
            ResponseGuard.ValidationResult validation = responseGuard.validate(response);
            if (!validation.checkValid()) {
                logger.warn("Chat 响应验证不通过: chatId={}, violations={}, originalResponse={}",
                        chatId, validation.violations(), truncate(response, 200));
            }
        }
        logger.info("Chat 完成: chatId={}, 耗时={}ms", chatId, elapsed);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "null";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}