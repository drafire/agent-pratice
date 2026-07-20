package com.drafire.tools;

import com.drafire.data.Booking;
import com.drafire.data.BookingStatus;
import com.drafire.serivce.FlightBookingService;
import com.drafire.interceptor.ResponseRenderer;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.function.Function;

@Configuration
@Validated
public class BookingTools {

    private final FlightBookingService flightBookingService;
    private final ResponseRenderer responseRenderer;

    public BookingTools(FlightBookingService flightBookingService, ResponseRenderer responseRenderer) {
        this.flightBookingService = flightBookingService;
        this.responseRenderer = responseRenderer;
    }

    public record BookingDetailRequest(@NotBlank(message = "预定编号不能为空") String bookingNumber,
                                       @NotBlank(message = "乘客姓名不能为空") String name) {
    }

    public record ChangeBookingDetailRequest(@NotBlank(message = "预定编号不能为空") String bookingNumber,
                                             @NotBlank(message = "乘客姓名不能为空") String name,
                                             LocalDate newDate,
                                             @NotBlank(message = "出发地不能为空") String from,
                                             @NotBlank(message = "目的地不能为空") String to) {
    }

    public record CancelBookingRequest(@NotBlank(message = "预定编号不能为空") String bookingNumber,
                                       @NotBlank(message = "乘客姓名不能为空") String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookingDetails(String bookingNumber, String name, LocalDate date, BookingStatus status, String from,
                                 String to, String bookingClass) {
    }

    @Bean(name = "queryFlightBookingDetails")
    @Description("根据预定编号和乘客姓名查询机票预定的详细信息，包括航班日期、状态、舱位等")
    @ToolFunction
    public Function<BookingDetailRequest, String> queryFlightBookingDetails() {
        return bookingDetailRequest -> {
            Booking booking = flightBookingService.getBooking(
                    bookingDetailRequest.bookingNumber, bookingDetailRequest.name);
            return responseRenderer.renderBooking(booking);
        };
    }

    @Bean(name = "modifyFlightBooking")
    @Description("修改已有机票预定的日期、出发地或目的地，需要提供预定编号、乘客姓名以及新的行程信息")
    @ToolFunction
    public Function<ChangeBookingDetailRequest, String> modifyFlightBooking() {
        return changeBookingDatesRequest -> {
            flightBookingService.changeBooking(
                    changeBookingDatesRequest.bookingNumber,
                    changeBookingDatesRequest.name,
                    changeBookingDatesRequest.newDate,
                    changeBookingDatesRequest.from,
                    changeBookingDatesRequest.to);
            return responseRenderer.renderModifySuccess(
                    changeBookingDatesRequest.bookingNumber,
                    changeBookingDatesRequest.from,
                    changeBookingDatesRequest.to,
                    changeBookingDatesRequest.newDate);
        };
    }

    @Bean(name = "cancelFlightBooking")
    @Description("取消指定的机票预定，需要提供预定编号和乘客姓名，取消后不可恢复")
    @ToolFunction
    public Function<CancelBookingRequest, String> cancelFlightBooking() {
        return cancelBookingRequest -> {
            flightBookingService.cancelBooking(
                    cancelBookingRequest.bookingNumber,
                    cancelBookingRequest.name);
            return responseRenderer.renderCancelSuccess(cancelBookingRequest.bookingNumber);
        };
    }

}