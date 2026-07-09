package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.drafire.data.Booking;
import com.drafire.security.ActionTokenService;
import com.drafire.serivce.FlightBookingService;
import com.drafire.serivce.FlightSearchService;
import com.drafire.serivce.WeatherService;
import com.drafire.interceptor.ResponseGuard;
import com.drafire.interceptor.ResponseRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ExecuteActionNode implements AsyncNodeAction {

    private static final Logger logger = LoggerFactory.getLogger(ExecuteActionNode.class);

    private final FlightBookingService flightBookingService;
    private final FlightSearchService flightSearchService;
    private final WeatherService weatherService;
    private final ResponseRenderer responseRenderer;
    private final ResponseGuard responseGuard;
    private final ActionTokenService actionTokenService;

    public ExecuteActionNode(FlightBookingService flightBookingService,
                             FlightSearchService flightSearchService,
                             WeatherService weatherService,
                             ResponseRenderer responseRenderer,
                             ResponseGuard responseGuard,
                             ActionTokenService actionTokenService) {
        this.flightBookingService = flightBookingService;
        this.flightSearchService = flightSearchService;
        this.weatherService = weatherService;
        this.responseRenderer = responseRenderer;
        this.responseGuard = responseGuard;
        this.actionTokenService = actionTokenService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String intent = state.value("intent", "GENERAL");
        logger.info("[执行动作] intent={}", intent);

        String rawResult = switch (intent) {
            case "BOOKING_DETAILS" -> executeQueryBooking(state);
            case "CHANGE_BOOKING" -> executeChangeBooking(state);
            case "CANCEL_BOOKING" -> executeCancelBooking(state);
            case "SEARCH_FLIGHTS" -> executeSearchFlights(state);
            case "WEATHER" -> executeQueryWeather(state);
            default -> null;
        };

        String toolResult = rawResult != null ? responseGuard.sanitize(rawResult) : null;

        return CompletableFuture.completedFuture(
                toolResult != null ? Map.of("toolResult", toolResult) : Map.of());
    }

    private String executeQueryBooking(OverAllState state) {
        String bookingNumber = state.value("bookingNumber", "");
        String customerName = state.value("customerName", "");
        logger.info("[查询订单] bookingNumber={}, customerName={}", bookingNumber, customerName);
        Booking booking = flightBookingService.getBooking(bookingNumber, customerName);
        return responseRenderer.renderBooking(booking);
    }

    /**
     * 改签操作：不再直接执行，而是生成一次性确认链接。
     * 用户必须点击链接并显式确认，后端才会执行写库操作。
     */
    private String executeChangeBooking(OverAllState state) {
        String bookingNumber = state.value("bookingNumber", "");
        String customerName = state.value("customerName", "");
        String from = state.value("from", "");
        String to = state.value("to", "");
        String dateStr = state.value("date", "");
        String chatId = state.value("chatId", "");

        logger.info("[改签-生成确认链接] bookingNumber={}, from={}, to={}, date={}", bookingNumber, from, to, dateStr);

        Booking booking = flightBookingService.getBooking(bookingNumber, customerName);
            String token = actionTokenService.createToken(
                    "CHANGE_BOOKING", bookingNumber, customerName, from, to, dateStr, chatId);

            return """
                    📋 改签确认

                    当前订单：%s
                    原行程：%s → %s（%s）
                    新行程：%s → %s（%s）

                    ⚠️ 请点击以下链接确认改签（10 分钟内有效）：
                    /confirm-action.html?token=%s

                    确认后将执行改签操作。""".formatted(
                    bookingNumber,
                    booking.getFrom(), booking.getTo(), booking.getDate(),
                    from, to, dateStr,
                    token);
    }

    /**
     * 取消操作：不再直接执行，而是生成一次性确认链接。
     * 用户必须点击链接并显式确认，后端才会执行写库操作。
     */
    private String executeCancelBooking(OverAllState state) {
        String bookingNumber = state.value("bookingNumber", "");
        String customerName = state.value("customerName", "");
        String chatId = state.value("chatId", "");

        logger.info("[取消订单-生成确认链接] bookingNumber={}", bookingNumber);

        Booking booking = flightBookingService.getBooking(bookingNumber, customerName);
            String token = actionTokenService.createToken(
                    "CANCEL_BOOKING", bookingNumber, customerName, "", "", "", chatId);

            String detail = responseRenderer.renderBooking(booking);
            return """
                    🗑️ 取消订单确认

                    %s

                    ⚠️ 取消后不可恢复，请点击以下链接确认（10 分钟内有效）：
                    /confirm-action.html?token=%s

                    确认后将执行取消操作。""".formatted(detail, token);
    }

    private String executeSearchFlights(OverAllState state) {
        String from = state.value("from", "");
        String to = state.value("to", "");
        String dateStr = state.value("date", "");
        logger.info("[搜索航班] from={}, to={}, date={}", from, to, dateStr);

        LocalDate date = null;
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception e) {
                logger.warn("[搜索航班] 日期解析失败: {}, 将不按日期过滤", dateStr);
            }
        }

        var flights = date != null
                ? flightSearchService.queryFlightsBetweenTwoCities(from, to, date)
                : flightSearchService.queryFlightsBetweenTwoCities(from, to);
        return responseRenderer.renderFlightList(flights, from, to);
    }

    private String executeQueryWeather(OverAllState state) {
        String city = state.value("weatherCity", "");
        int days = state.value("weatherDays", 0);
        logger.info("[查询天气] city={}, days={}", city, days);
        return weatherService.getWeather(city, days);
    }
}