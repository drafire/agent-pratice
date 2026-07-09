package com.drafire.security;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 操作令牌服务。
 * 基于 JWT 签名生成一次性操作令牌，操作详情编码在令牌自身中，防篡改。
 * 通过已消费令牌集合实现一次性使用，防止重放攻击。
 */
@Service
public class ActionTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ActionTokenService.class);

    private static final long TOKEN_TTL_SECONDS = 600; // 10 分钟
    private static final long TOKEN_TTL_MILLIS = TOKEN_TTL_SECONDS * 1000;

    private final JwtUtil jwtUtil;

    /**
     * 已消费的令牌 JTI（JWT ID），用于保证一次性使用。
     * 生产环境应替换为 Redis，key 带 TTL 自动过期。
     */
    private final Set<String> consumedTokens = ConcurrentHashMap.newKeySet();

    /**
     * 已消费令牌的消费时间戳，用于按时间淘汰过期记录。
     */
    private final Map<String, Long> consumedTokenTimestamps = new ConcurrentHashMap<>();

    public ActionTokenService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 生成操作确认令牌（JWT 签名）。
     */
    public String createToken(String actionType, String bookingNumber, String customerName,
                              String from, String to, String date, String chatId) {
        Map<String, String> claims = Map.of(
                "actionType", actionType,
                "bookingNumber", bookingNumber,
                "customerName", customerName,
                "from", from,
                "to", to,
                "date", date,
                "chatId", chatId
        );

        String token = jwtUtil.generateActionToken(claims, TOKEN_TTL_SECONDS);
        logger.info("操作令牌已生成: actionType={}, bookingNumber={}", actionType, bookingNumber);
        return token;
    }

    /**
     * 解析并消费令牌（原子操作）。
     * 使用 ConcurrentHashMap.newKeySet().add() 的返回值保证检查和标记是原子的，
     * 消除 TOCTOU 竞态条件。
     * JWT 解析异常（过期、签名无效等）直接向上抛出，由 GlobalExceptionHandler 统一处理。
     */
    public Claims parseAndConsume(String token) {
        Claims claims = jwtUtil.parseActionToken(token);

        String jti = claims.getId();
        if (jti != null && !consumedTokens.add(jti)) {
            throw new IllegalArgumentException("操作令牌已被使用");
        }
        if (jti != null) {
            consumedTokenTimestamps.put(jti, System.currentTimeMillis());
        }

        logger.info("操作令牌已消费: actionType={}", claims.get("actionType"));
        return claims;
    }

    /**
     * 仅解析并验证令牌（不消费），用于查看操作详情。
     */
    public Claims parseToken(String token) {
        Claims claims = jwtUtil.parseActionToken(token);

        String jti = claims.getId();
        if (jti != null && consumedTokens.contains(jti)) {
            throw new IllegalArgumentException("操作令牌已被使用");
        }

        return claims;
    }

    /**
     * 每 5 分钟清理过期令牌的消费记录。
     * 根据时间戳驱逐超过 TTL 的记录，而非全量清空，避免误伤有效令牌。
     * 生产环境使用 Redis 时无需此方法（TTL 自动过期）。
     */
    @Scheduled(fixedRate = 300_000)
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        long expireThreshold = now - TOKEN_TTL_MILLIS;

        int removed = 0;
        for (Map.Entry<String, Long> entry : consumedTokenTimestamps.entrySet()) {
            if (entry.getValue() < expireThreshold) {
                consumedTokens.remove(entry.getKey());
                consumedTokenTimestamps.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("令牌消费记录已清理: 移除 {} 条过期记录, 剩余 {} 条",
                    removed, consumedTokens.size());
        }
    }
}