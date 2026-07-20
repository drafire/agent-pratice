package com.drafire.controller;

import com.drafire.data.Booking;
import com.drafire.security.ActionTokenService;
import com.drafire.serivce.FlightBookingService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 操作确认控制器。
 * 用户通过 LLM 生成的确认链接打开页面后，显式提交确认请求。
 * 所有写操作必须经过此接口 + JWT 鉴权，LLM 不能直接执行。
 * 异常统一由 GlobalExceptionHandler 处理，Controller 层无需 try-catch。
 */
@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private static final Logger logger = LoggerFactory.getLogger(ActionController.class);

    private final ActionTokenService tokenService;
    private final FlightBookingService flightBookingService;

    public ActionController(ActionTokenService tokenService, FlightBookingService flightBookingService) {
        this.tokenService = tokenService;
        this.flightBookingService = flightBookingService;
    }

    /**
     * 获取操作详情（打开确认页面时调用）。
     * 需要 JWT 鉴权。
     */
    @GetMapping("/{tokenId}")
    public ResponseEntity<Map<String, Object>> getActionDetail(@PathVariable String tokenId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        Claims claims = tokenService.parseToken(tokenId);

        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("token", tokenId);
        detail.put("actionType", claims.get("actionType", String.class));
        detail.put("bookingNumber", claims.get("bookingNumber", String.class));
        detail.put("customerName", claims.get("customerName", String.class));
        detail.put("from", claims.get("from", String.class));
        detail.put("to", claims.get("to", String.class));
        detail.put("date", claims.get("date", String.class));
        detail.put("expiresAt", claims.getExpiration().toString());

        String actionType = claims.get("actionType", String.class);
        if ("CANCEL_BOOKING".equals(actionType)) {
            String bookingNumber = claims.get("bookingNumber", String.class);
            String customerName = claims.get("customerName", String.class);
            Booking booking = flightBookingService.getBooking(bookingNumber, customerName);
            detail.put("bookingDetail", Map.of(
                    "date", booking.getDate().toString(),
                    "from", booking.getOrigin(),
                    "to", booking.getDestination(),
                    "bookingClass", booking.getBookingClass(),
                    "status", booking.getBookingStatus()
            ));
        }

        return ResponseEntity.ok(detail);
    }

    /**
     * 确认并执行操作（用户点击确认按钮时调用）。
     * 需要 JWT 鉴权，确保只有登录用户才能执行写操作。
     */
    @PostMapping("/confirm/{tokenId}")
    public ResponseEntity<Map<String, Object>> confirmAction(@PathVariable String tokenId, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        // 先原子消费令牌（防止并发重复执行），再执行业务逻辑。
        // 如果业务失败，令牌已消费，用户需重新生成——这是有意为之：
        // 双重执行数据库写操作的风险远大于浪费一个令牌。
        Claims claims = tokenService.parseAndConsume(tokenId);

        String actionType = claims.get("actionType", String.class);
        if (actionType == null) {
            throw new IllegalArgumentException("操作类型缺失");
        }
        String result = switch (actionType) {
            case "CHANGE_BOOKING" -> executeChange(claims);
            case "CANCEL_BOOKING" -> executeCancel(claims);
            default -> throw new IllegalArgumentException("不支持的操作类型: " + actionType);
        };

        logger.info("操作执行成功: actionType={}, user={}", actionType, principal.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", result));
    }

    private String executeChange(Claims claims) {
        String bookingNumber = claims.get("bookingNumber", String.class);
        String customerName = claims.get("customerName", String.class);
        String from = claims.get("from", String.class);
        String to = claims.get("to", String.class);
        String dateStr = claims.get("date", String.class);

        LocalDate date = dateStr != null && !dateStr.isEmpty()
                ? LocalDate.parse(dateStr) : LocalDate.now().plusDays(1);
        flightBookingService.changeBooking(bookingNumber, customerName, date, from, to);
        return "改签成功！订单 " + bookingNumber + " 已改为 " + date + " " + from + " → " + to;
    }

    private String executeCancel(Claims claims) {
        String bookingNumber = claims.get("bookingNumber", String.class);
        String customerName = claims.get("customerName", String.class);
        flightBookingService.cancelBooking(bookingNumber, customerName);
        return "取消成功！订单 " + bookingNumber + " 已取消。";
    }
}