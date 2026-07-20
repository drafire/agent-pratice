package com.drafire.interceptor;

import com.drafire.config.FeatureFlags;
import com.drafire.config.GraphMetrics;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Aspect
@Component
public class ChatGlobalAspect {

    private static final Logger logger = LoggerFactory.getLogger(ChatGlobalAspect.class);

    private static final String SAFE_FALLBACK = "抱歉，服务暂时不可用，请稍后再试。";
    private static final String REJECT_MESSAGE = "抱歉，无法处理该请求。";

    private static final int MAX_INPUT_LENGTH = 2000;

    /**
     * 提示词注入检测规则，覆盖常见的攻击手法。
     * 命中任一规则即拦截，返回 REJECT_MESSAGE。
     */
    private static final Pattern[] INJECTION_PATTERNS = {

            // 1. 指令覆盖：试图覆盖系统提示词
            Pattern.compile("忽略.*(指令|规则|限制|约束|设置|上述|以上|之前|先前)|" +
                            "ignore.*(instruction|rule|restriction|constraint|above|previous|prior|limit)|" +
                            "forget.*(rule|instruction|everything|all)|" +
                            "忘记.*(规则|指令|一切|所有)|" +
                            "override.*(instruction|rule|prompt|system)|" +
                            "覆盖.*(指令|规则|提示词|系统)",
                    Pattern.CASE_INSENSITIVE),

            // 2. 角色扮演越狱：DAN / 强制角色切换
            Pattern.compile("你是.*DAN|Do Anything Now|你现在是.*角色|" +
                            "act as.*(developer|hacker|expert|unfiltered)|" +
                            "扮演.*(黑客|开发者|无限制|专家)|" +
                            "你不再是.*(助手|客服|AI)|" +
                            "you are no longer.*(assistant|AI|helper)",
                    Pattern.CASE_INSENSITIVE),

            // 3. 提示词探测：试图获取系统提示词
            Pattern.compile("系统提示词|system prompt|system message|你的设定|你的配置|" +
                            "your (instruction|guideline|rule|configuration|setting|training)|" +
                            "show.*(prompt|instruction|system)|" +
                            "打印.*(提示词|指令|设定|配置)|" +
                            "返回.*(原始|完整).*(指令|提示词|设定)|" +
                            "你.*(被|如何).*(创建|训练|设定|配置)",
                    Pattern.CASE_INSENSITIVE),

            // 4. 代码/脚本执行注入
            Pattern.compile("执行.*(脚本|命令|代码|shell|python|sql|bash|cmd)|" +
                            "run.*(script|command|code|shell|python|sql|bash)|" +
                            "\\beval\\s*\\(|\\bexec\\s*\\(|__import__\\s*\\(|" +
                            "os\\.system|subprocess|rm\\s+-rf|chmod\\s+|" +
                            "wget\\s+|curl\\s+|\\bDROP\\s+TABLE|\\bDELETE\\s+FROM|" +
                            "import\\s+(os|sys|subprocess|shutil|socket|requests)",
                    Pattern.CASE_INSENSITIVE),

            // 5. 系统操控：试图修改行为模式
            Pattern.compile("从现在开始.*(你|你要|你必须|你只能|你需要)|" +
                            "from now on.*(you are|you must|you will|you should)|" +
                            "你.*(必须|一定要|只能).*(回答|输出|回复|告诉我)|" +
                            "you (must|have to|should|will).*(answer|respond|output|tell|say)|" +
                            "不要.*(拒绝|推脱|回避|说.*不)|" +
                            "do not.*(refuse|deny|reject|say no)|" +
                            "移除.*(所有|一切|任何).*(限制|约束|规则)|" +
                            "remove.*(all|any|every).*(restriction|constraint|rule|limit)",
                    Pattern.CASE_INSENSITIVE),

            // 6. 分隔符逃逸：试图跳出模板
            Pattern.compile("\\}{3,}|\\]{3,}|\\]{3,}|" +
                            "```.*```|~~~~|___|" +
                            "<\\|.*\\|>|\\[\\[.*\\]\\]|" +
                            "\\{%.*%\\}|\\{\\{.*\\}\\}",
                    Pattern.CASE_INSENSITIVE),

            // 7. 重复/轰炸攻击：大量重复内容消耗 token
            Pattern.compile("(.)\\1{100,}|" +
                            "(.{5,})\\1{20,}",
                    Pattern.CASE_INSENSITIVE),

            // 8. 上下文欺骗：利用多轮对话伪造上下文
            Pattern.compile("(你之前|你上面|你刚刚|你刚才|你上次|你已经).*(说|回答|回复|告诉|输出|展示|给出)|" +
                            "(you (said|answered|replied|told|output|showed|gave)|" +
                            "you (previously|already|just|above)).*(said|answered|replied|told|output|showed)|" +
                            "我(确认|同意|批准|授权).*(取消|删除|修改|退款|赔偿)",
                    Pattern.CASE_INSENSITIVE),

            // 9. 编码/翻译绕过：利用间接方式注入
            Pattern.compile("翻译.*(以下|下面|这段|这个).*(内容|文本|文字|话)|" +
                            "translate.*(following|below|this).*(content|text|words|sentence)|" +
                            "解码.*(以下|下面|这段|这个)|" +
                            "decode.*(following|below|this)|" +
                            "base64.*decode|请用.*(中文|英文).*重复|" +
                            "please.*repeat.*in.*(chinese|english)",
                    Pattern.CASE_INSENSITIVE),

            // 10. 敏感信息探测：试图获取密钥/配置
            Pattern.compile("API.*(key|密钥|token|密码|secret)|" +
                            "api.*(key|secret|token|password)|" +
                            "JWT.*(secret|密钥|token)|" +
                            "数据库.*(密码|账号|连接|地址)|" +
                            "database.*(password|credential|connection|host)|" +
                            "环境变量|environment.*variable|\\.env",
                    Pattern.CASE_INSENSITIVE),
    };

    private final ResponseGuard responseGuard;
    private final FeatureFlags featureFlags;
    private final Tracer tracer;
    private final GraphMetrics graphMetrics;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ChatGlobalAspect(ResponseGuard responseGuard, FeatureFlags featureFlags, Tracer tracer,
                            GraphMetrics graphMetrics, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.responseGuard = responseGuard;
        this.featureFlags = featureFlags;
        this.tracer = tracer;
        this.graphMetrics = graphMetrics;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Pointcut("execution(* com.drafire.serivce.CustomerSupportAssistant.chat(..))")
    public void functionCallingChat() {
    }

    @Pointcut("execution(* com.drafire.graph.GraphAssistantController.chat(..))")
    public void graphChat() {
    }

    @Around("functionCallingChat()")
    @Bulkhead(name = "chat-api", fallbackMethod = "bulkheadFallback")
    @RateLimiter(name = "chat-api", fallbackMethod = "rateLimiterFallback")
    @Retry(name = "llm-retry", fallbackMethod = "retryFallback")
    public Object aroundFunctionCalling(ProceedingJoinPoint pjp) throws Throwable {
        return aroundChat(pjp, "FunctionCalling");
    }

    @Around("graphChat()")
    @Bulkhead(name = "chat-api", fallbackMethod = "bulkheadFallbackGraph")
    @RateLimiter(name = "chat-api", fallbackMethod = "rateLimiterFallbackGraph")
    @Retry(name = "llm-retry", fallbackMethod = "retryFallbackGraph")
    public Object aroundGraph(ProceedingJoinPoint pjp) throws Throwable {
        return aroundChat(pjp, "Graph");
    }

    @SuppressWarnings("unchecked")
    private Object aroundChat(ProceedingJoinPoint pjp, String mode) throws Throwable {
        Timer.Sample sample = graphMetrics.startRequestTimer();
        Object[] args = pjp.getArgs();
        String chatId = (String) args[0];
        String userMessage = (String) args[1];

        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("chatId", chatId);
            currentSpan.tag("mode", mode);
        }
        MDC.put("chatId", chatId);
        MDC.put("mode", mode);

        // 输入治理（可通过 feature flags 动态关闭）
        if (featureFlags.isInputGuardEnabled()) {
            InputGuardResult guardResult = guardInput(chatId, userMessage, mode);
            if (guardResult.blocked()) {
                logger.warn("输入被拦截: reason={}", guardResult.reason());
                graphMetrics.recordInputGuarded(guardResult.reason());
                graphMetrics.recordRequest(mode, "unknown", "blocked");
                graphMetrics.stopRequestTimer(sample, mode, "unknown");
                MDC.remove("chatId");
                MDC.remove("mode");
                if ("Graph".equals(mode)) {
                    return Flux.just(ServerSentEvent.<String>builder().data(REJECT_MESSAGE).build());
                }
                return Flux.just(REJECT_MESSAGE);
            }
            args[1] = guardResult.sanitizedInput();
        }

        logger.info("Chat 请求: message={}", args[1]);
        graphMetrics.recordRequest(mode, "unknown", "accepted");

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("llm-call");
        var metrics = circuitBreaker.getMetrics();
        logger.info("熔断器状态: mode={}, state={}, failures={}, success={}, failureRate={}%, notPermitted={}",
                mode,
                circuitBreaker.getState(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getFailureRate(),
                metrics.getNumberOfNotPermittedCalls());

        if (!circuitBreaker.tryAcquirePermission()) {
            long waitInOpen = circuitBreaker.getMetrics().getNumberOfNotPermittedCalls();
            logger.warn("熔断已打开: mode={}, notPermittedCalls={}", mode, waitInOpen);
            graphMetrics.recordError(mode, "CircuitBreakerOpen");
            graphMetrics.stopRequestTimer(sample, mode, "unknown");
            MDC.remove("chatId");
            MDC.remove("mode");
            if ("Graph".equals(mode)) {
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("服务暂时不可用，系统正在恢复中，请稍后再试。").build());
            }
            return Flux.just("服务暂时不可用，系统正在恢复中，请稍后再试。");
        }

        long start = System.currentTimeMillis();
        try {
            Object rawResult = pjp.proceed();

            if (rawResult instanceof Flux<?> flux) {
                if ("Graph".equals(mode)) {
                    return attachGraphHooks(start, (Flux<ServerSentEvent<String>>) flux, sample, mode, circuitBreaker);
                } else {
                    return attachFunctionCallingHooks(start, (Flux<String>) flux, sample, mode, circuitBreaker);
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            circuitBreaker.onSuccess(elapsed, TimeUnit.MILLISECONDS);
            MDC.remove("chatId");
            MDC.remove("mode");
            return rawResult;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            circuitBreaker.onError(elapsed, TimeUnit.MILLISECONDS, e);
            logger.error("Chat 同步异常: 耗时={}ms, error={}", elapsed, e.getMessage());
            graphMetrics.recordError(mode, e.getClass().getSimpleName());
            graphMetrics.stopRequestTimer(sample, mode, "unknown");
            MDC.remove("chatId");
            MDC.remove("mode");
            throw e;
        }

    }

    /**
     * 输入治理：对用户输入做安全检查，返回治理结果。
     * 注入检测命中直接拦截，超长输入截断处理。
     */
    private InputGuardResult guardInput(String chatId, String rawInput, String mode) {
        if (rawInput == null || rawInput.isEmpty()) {
            return InputGuardResult.blocked("输入为空");
        }

        String sanitized = rawInput;

        if (sanitized.length() > MAX_INPUT_LENGTH) {
            logger.warn("输入过长被截断: chatId={}, originalLength={}", chatId, sanitized.length());
            sanitized = sanitized.substring(0, MAX_INPUT_LENGTH);
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                logger.warn("检测到提示词注入已拦截: chatId={}, pattern={}, input={}",
                        chatId, pattern.pattern(), truncate(sanitized, 200));
                return InputGuardResult.blocked("检测到提示词注入");
            }
        }

        return InputGuardResult.passed(sanitized);
    }

    private record InputGuardResult(String reason, String sanitizedInput) {

        static InputGuardResult blocked(String reason) {
            return new InputGuardResult(reason, "");
        }

        static InputGuardResult passed(String sanitizedInput) {
            return new InputGuardResult("", sanitizedInput);
        }

        boolean blocked() {
            return !reason.isEmpty();
        }
    }

    private Flux<String> attachFunctionCallingHooks(long start, Flux<String> result, Timer.Sample sample,
                                                    String mode, CircuitBreaker circuitBreaker) {
        StringBuffer fullResponse = new StringBuffer();
        Map<String, String> mdc = MDC.getCopyOfContextMap();

        return result
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    restoreMdc(mdc);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        validateAndLog(elapsed, fullResponse.toString());
                        circuitBreaker.onSuccess(elapsed, TimeUnit.MILLISECONDS);
                    } finally {
                        MDC.clear();
                    }
                    graphMetrics.stopRequestTimer(sample, mode, "unknown");
                })
                .doOnError(error -> {
                    restoreMdc(mdc);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        circuitBreaker.onError(elapsed, TimeUnit.MILLISECONDS, error);
                        var cbMetrics = circuitBreaker.getMetrics();
                        logger.error("Chat 异常-熔断器: 耗时={}ms, error={}, failures={}, success={}, failureRate={}%",
                                elapsed, error.getMessage(),
                                cbMetrics.getNumberOfFailedCalls(),
                                cbMetrics.getNumberOfSuccessfulCalls(),
                                cbMetrics.getFailureRate());
                    } finally {
                        MDC.clear();
                    }
                    graphMetrics.recordError(mode, error.getClass().getSimpleName());
                    graphMetrics.stopRequestTimer(sample, mode, "unknown");
                })
                .onErrorResume(error -> Flux.just(SAFE_FALLBACK));
    }

    private Flux<ServerSentEvent<String>> attachGraphHooks(long start, Flux<ServerSentEvent<String>> result,
                                                      Timer.Sample sample, String mode, CircuitBreaker circuitBreaker) {
        StringBuffer fullResponse = new StringBuffer();
        Map<String, String> mdc = MDC.getCopyOfContextMap();

        return result
                .doOnNext(event -> {
                    if (event != null && event.data() != null) {
                        fullResponse.append(event.data());
                    }
                })
                .doOnComplete(() -> {
                    restoreMdc(mdc);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        validateAndLog(elapsed, fullResponse.toString());
                        circuitBreaker.onSuccess(elapsed, TimeUnit.MILLISECONDS);
                        var cbMetrics = circuitBreaker.getMetrics();
                        logger.info("🔵 [Aspect] Graph 正常完成: 耗时={}ms, state={}, failures={}, success={}",
                                elapsed, circuitBreaker.getState(),
                                cbMetrics.getNumberOfFailedCalls(),
                                cbMetrics.getNumberOfSuccessfulCalls());
                    } finally {
                        MDC.clear();
                    }
                    graphMetrics.stopRequestTimer(sample, mode, "unknown");
                })
                .doOnError(error -> {
                    // ════════════════════════════════════════════════════════════════
                    // 关键！这是真正触发 circuitBreaker.onError() 的地方
                    // L246 (aroundChat() 的 catch 块) 永远不会被调用！
                    // 因为 Controller 返回的是 Flux，pjp.proceed() 只是创建 Flux 不抛异常
                    // ════════════════════════════════════════════════════════════════
                    restoreMdc(mdc);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        circuitBreaker.onError(elapsed, TimeUnit.MILLISECONDS, error);
                        var cbMetrics = circuitBreaker.getMetrics();
                        logger.error("🔴 [Aspect] Graph 异常-熔断器计数: 耗时={}ms, state={}, error={}, failures={}, success={}, failureRate={}%",
                                elapsed, circuitBreaker.getState(), error.getMessage(),
                                cbMetrics.getNumberOfFailedCalls(),
                                cbMetrics.getNumberOfSuccessfulCalls(),
                                cbMetrics.getFailureRate());
                    } finally {
                        MDC.clear();
                    }
                    graphMetrics.recordError(mode, error.getClass().getSimpleName());
                    graphMetrics.stopRequestTimer(sample, mode, "unknown");
                })
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .data(SAFE_FALLBACK).build()));
    }

    /**
     * 合并恢复 MDC 上下文，使用逐个 put 而非 setContextMap，
     * 避免覆盖 Reactor 自动传播的 traceId/spanId。 
     */
    private void restoreMdc(Map<String, String> mdc) {
        if (mdc != null) {
            mdc.forEach(MDC::put);
        }
    }

    private void validateAndLog(long elapsed, String response) {
        if (featureFlags.isOutputGuardEnabled() && response != null && !response.isEmpty()) {
            ResponseGuard.ValidationResult validation = responseGuard.validate(response);
            if (!validation.checkValid()) {
                logger.warn("Chat 响应验证不通过: violations={}, originalResponse={}",
                        validation.violations(), truncate(response, 200));
            }
        }
        logger.info("Chat 完成: 耗时={}ms", elapsed);
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

    // ==================== FunctionCalling 模式 fallback ====================

    public Flux<String> circuitBreakerFallback(ProceedingJoinPoint pjp, Throwable t) {
        logger.error("熔断触发: mode=FunctionCalling, error={}", t.getMessage());
        graphMetrics.recordError("FunctionCalling", "CircuitBreakerOpen");
        return Flux.just("服务暂时不可用，系统正在恢复中，请稍后再试。");
    }

    public Flux<String> rateLimiterFallback(ProceedingJoinPoint pjp, Throwable t) {
        logger.warn("限流触发: mode=FunctionCalling");
        graphMetrics.recordError("FunctionCalling", "RateLimited");
        return Flux.just("请求过于频繁，请稍后再试。");
    }

    public Flux<String> retryFallback(ProceedingJoinPoint pjp, Throwable t) {
        logger.error("重试耗尽: mode=FunctionCalling, error={}", t.getMessage());
        graphMetrics.recordError("FunctionCalling", "RetryExhausted");
        return Flux.just("服务暂时不可用，请稍后再试。");
    }

    public Flux<String> bulkheadFallback(ProceedingJoinPoint pjp, Throwable t) {
        logger.warn("舱壁满: mode=FunctionCalling");
        graphMetrics.recordError("FunctionCalling", "BulkheadFull");
        return Flux.just("系统繁忙，请稍后再试。");
    }

    // ==================== Graph 模式 fallback ====================

    public Flux<ServerSentEvent<String>> circuitBreakerFallbackGraph(ProceedingJoinPoint pjp, Throwable t) {
        logger.error("熔断触发: mode=Graph, error={}", t.getMessage());
        graphMetrics.recordError("Graph", "CircuitBreakerOpen");
        return Flux.just(ServerSentEvent.<String>builder().data("服务暂时不可用，系统正在恢复中，请稍后再试。").build());
    }

    public Flux<ServerSentEvent<String>> rateLimiterFallbackGraph(ProceedingJoinPoint pjp, Throwable t) {
        logger.warn("限流触发: mode=Graph");
        graphMetrics.recordError("Graph", "RateLimited");
        return Flux.just(ServerSentEvent.<String>builder().data("请求过于频繁，请稍后再试。").build());
    }

    public Flux<ServerSentEvent<String>> retryFallbackGraph(ProceedingJoinPoint pjp, Throwable t) {
        logger.error("重试耗尽: mode=Graph, error={}", t.getMessage());
        graphMetrics.recordError("Graph", "RetryExhausted");
        return Flux.just(ServerSentEvent.<String>builder().data("服务暂时不可用，请稍后再试。").build());
    }

    public Flux<ServerSentEvent<String>> bulkheadFallbackGraph(ProceedingJoinPoint pjp, Throwable t) {
        logger.warn("舱壁满: mode=Graph");
        graphMetrics.recordError("Graph", "BulkheadFull");
        return Flux.just(ServerSentEvent.<String>builder().data("系统繁忙，请稍后再试。").build());
    }
}