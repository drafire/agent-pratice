package com.drafire.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Around("execution(* com.drafire.serivce.CustomerSupportAssistant.chat(..))")
    public Object around(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        String chatId = (String) args[0];
        String userMessage = (String) args[1];

        logger.info("Chat 请求: chatId={}, message={}", chatId, userMessage);

        long start = System.currentTimeMillis();
        try {
            @SuppressWarnings("unchecked")
            Flux<String> result = (Flux<String>) pjp.proceed();

            StringBuilder fullResponse = new StringBuilder();

            return result
                    .doOnNext(chunk -> fullResponse.append(chunk))
                    .doOnComplete(() -> {
                        long elapsed = System.currentTimeMillis() - start;
                        String response = fullResponse.toString();
                        ResponseGuard.ValidationResult validation = responseGuard.validate(response);
                        if (!validation.checkValid()) {
                            logger.warn("Chat 响应验证不通过: chatId={}, violations={}, originalResponse={}",
                                    chatId, validation.violations(), truncate(response, 200));
                        }
                        logger.info("Chat 完成: chatId={}, 耗时={}ms", chatId, elapsed);
                    })
                    .doOnError(error -> {
                        long elapsed = System.currentTimeMillis() - start;
                        logger.error("Chat 异常: chatId={}, 耗时={}ms, error={}", chatId, elapsed, error.getMessage());
                    })
                    .onErrorResume(error -> Flux.just(SAFE_FALLBACK));
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            logger.error("Chat 同步异常: chatId={}, 耗时={}ms, error={}", chatId, elapsed, e.getMessage());
            return Flux.just(SAFE_FALLBACK);
        }
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