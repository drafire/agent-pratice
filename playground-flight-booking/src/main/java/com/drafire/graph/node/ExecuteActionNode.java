package com.drafire.graph.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.drafire.data.Booking;
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

    public ExecuteActionNode(FlightBookingService flightBookingService,
                             FlightSearchService flightSearchService,
                             WeatherService weatherService,
                             ResponseRenderer responseRenderer,
                             ResponseGuard responseGuard) {
        this.flightBookingService = flightBookingService;
        this.flightSearchService = flightSearchService;
        this.weatherService = weatherService;
        this.responseRenderer = responseRenderer;
        this.responseGuard = responseGuard;
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

    private String executeChangeBooking(OverAllState state) {
        String bookingNumber = state.value("bookingNumber", "");
        String customerName = state.value("customerName", "");
        String from = state.value("from", "");
        String to = state.value("to", "");
        String dateStr = state.value("date", "");
        LocalDate date = dateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(dateStr);
        logger.info("[改签] bookingNumber={}, from={}, to={}, date={}", bookingNumber, from, to, date);

        flightBookingService.changeBooking(bookingNumber, customerName, date, from, to);
        return responseRenderer.renderModifySuccess(bookingNumber, from, to, date);
    }

    private String executeCancelBooking(OverAllState state) {
        String bookingNumber = state.value("bookingNumber", "");
        String customerName = state.value("customerName", "");
        Boolean confirmed = state.value("cancelConfirmed", false);

        if (!confirmed) {
            logger.info("[取消订单-HITL] 查询订单详情供确认: bookingNumber={}", bookingNumber);
            Booking booking = flightBookingService.getBooking(bookingNumber, customerName);
            String detail = responseRenderer.renderBooking(booking);
            return """
                    ⚠️ 请确认要取消以下订单（取消后不可恢复）：

                    %s

                    请回复"确认取消"来执行取消操作，或回复"不取消"来放弃。""".formatted(detail);
        } else {
            logger.info("[取消订单-执行] 用户已确认: bookingNumber={}", bookingNumber);
            flightBookingService.cancelBooking(bookingNumber, customerName);
            return responseRenderer.renderCancelSuccess(bookingNumber);
        }
    }

    private String executeSearchFlights(OverAllState state) {
        String from = state.value("from", "");
        String to = state.value("to", "");
        logger.info("[搜索航班] from={}, to={}", from, to);

        var flights = flightSearchService.queryFlightsBetweenTwoCities(from, to);
        return responseRenderer.renderFlightList(flights, from, to);
    }

    private String executeQueryWeather(OverAllState state) {
        String city = state.value("weatherCity", "");
        int days = state.value("weatherDays", 0);
        logger.info("[查询天气] city={}, days={}", city, days);
        return weatherService.getWeather(city, days);
    }
}