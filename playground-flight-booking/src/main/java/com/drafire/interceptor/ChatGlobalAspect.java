package com.drafire.interceptor;

import com.drafire.config.FeatureFlags;
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

import java.util.UUID;
import java.util.regex.Pattern;

@Aspect
@Component
public class ChatGlobalAspect {

    private static final Logger logger = LoggerFactory.getLogger(ChatGlobalAspect.class);

    private static final String SAFE_FALLBACK = "抱歉，服务暂时不可用，请稍后再试。";
    private static final String REJECT_MESSAGE = "抱歉，无法处理该请求。";

    private static final int MAX_INPUT_LENGTH = 2000;

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_CHAT_ID = "chatId";
    private static final String MDC_MODE = "mode";

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

    public ChatGlobalAspect(ResponseGuard responseGuard, FeatureFlags featureFlags) {
        this.responseGuard = responseGuard;
        this.featureFlags = featureFlags;
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

        // 注入 traceId 到 MDC，全链路日志可追踪
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_CHAT_ID, chatId);
        MDC.put(MDC_MODE, mode);

        try {
            // 输入治理（可通过 feature flags 动态关闭）
            if (featureFlags.isInputGuardEnabled()) {
                InputGuardResult guardResult = guardInput(chatId, userMessage);
                if (guardResult.blocked()) {
                    logger.warn("输入被拦截: reason={}", guardResult.reason());
                    if ("Graph".equals(mode)) {
                        return Flux.just(ServerSentEvent.<String>builder().data(REJECT_MESSAGE).build());
                    }
                    return Flux.just(REJECT_MESSAGE);
                }
                args[1] = guardResult.sanitizedInput();
            }

            logger.info("Chat 请求: message={}", args[1]);

            long start = System.currentTimeMillis();
            try {
                Object rawResult = pjp.proceed();

                if (rawResult instanceof Flux<?> flux) {
                    if ("Graph".equals(mode)) {
                        return attachGraphHooks(chatId, start, traceId, (Flux<ServerSentEvent<String>>) flux);
                    } else {
                        return attachFunctionCallingHooks(chatId, start, traceId, (Flux<String>) flux);
                    }
                }

                return rawResult;
            } catch (Throwable e) {
                long elapsed = System.currentTimeMillis() - start;
                logger.error("Chat 同步异常: 耗时={}ms, error={}", elapsed, e.getMessage());
                return Flux.just(SAFE_FALLBACK);
            }
        } finally {
            // 清理 MDC，防止线程池复用导致数据错乱
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_CHAT_ID);
            MDC.remove(MDC_MODE);
        }
    }

    /**
     * 输入治理：对用户输入做安全检查，返回治理结果。
     * 注入检测命中直接拦截，超长输入截断处理。
     */
    private InputGuardResult guardInput(String chatId, String rawInput) {
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

    private Flux<String> attachFunctionCallingHooks(String chatId, long start, String traceId, Flux<String> result) {
        StringBuffer fullResponse = new StringBuffer();

        return result
                .doOnNext(chunk -> fullResponse.append(chunk))
                .doOnComplete(() -> {
                    MDC.put(MDC_TRACE_ID, traceId);
                    MDC.put(MDC_CHAT_ID, chatId);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        validateAndLog(elapsed, fullResponse.toString());
                    } finally {
                        MDC.remove(MDC_TRACE_ID);
                        MDC.remove(MDC_CHAT_ID);
                    }
                })
                .doOnError(error -> {
                    MDC.put(MDC_TRACE_ID, traceId);
                    MDC.put(MDC_CHAT_ID, chatId);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        logger.error("Chat 异常: 耗时={}ms, error={}", elapsed, error.getMessage());
                    } finally {
                        MDC.remove(MDC_TRACE_ID);
                        MDC.remove(MDC_CHAT_ID);
                    }
                })
                .onErrorResume(error -> Flux.just(SAFE_FALLBACK));
    }

    private Flux<ServerSentEvent<String>> attachGraphHooks(String chatId, long start, String traceId,
                                                            Flux<ServerSentEvent<String>> result) {
        StringBuffer fullResponse = new StringBuffer();

        return result
                .doOnNext(event -> {
                    if (event != null && event.data() != null) {
                        fullResponse.append(event.data());
                    }
                })
                .doOnComplete(() -> {
                    MDC.put(MDC_TRACE_ID, traceId);
                    MDC.put(MDC_CHAT_ID, chatId);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        validateAndLog(elapsed, fullResponse.toString());
                    } finally {
                        MDC.remove(MDC_TRACE_ID);
                        MDC.remove(MDC_CHAT_ID);
                    }
                })
                .doOnError(error -> {
                    MDC.put(MDC_TRACE_ID, traceId);
                    MDC.put(MDC_CHAT_ID, chatId);
                    try {
                        long elapsed = System.currentTimeMillis() - start;
                        logger.error("Graph Chat 异常: 耗时={}ms, error={}", elapsed, error.getMessage());
                    } finally {
                        MDC.remove(MDC_TRACE_ID);
                        MDC.remove(MDC_CHAT_ID);
                    }
                })
                .onErrorResume(error -> Flux.just(ServerSentEvent.<String>builder()
                        .data(SAFE_FALLBACK).build()));
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
}