package com.drafire.tools;

import com.drafire.data.Booking;
import com.drafire.data.BookingStatus;
import com.drafire.serivce.FlightBookingService;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDate;
import java.util.function.Function;

@Configuration
public class BookingTools {
    private static final Logger logger = LoggerFactory.getLogger(BookingTools.class);

    private final FlightBookingService flightBookingService;

    public BookingTools(FlightBookingService flightBookingService) {
        this.flightBookingService = flightBookingService;
    }

    public record BookingDetailRequest(String bookingNumber, String name) {
    }

    public record ChangeBookingDetailRequest(String bookingNumber, String name, LocalDate newDate, String from,
                                             String to) {
    }

    public record CancelBookingRequest(String bookingNumber, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookingDetails(String bookingNumber, String name, LocalDate date, BookingStatus status, String from,
                                 String to, String bookingClass) {
    }

    @Bean(name = "queryFlightBookingDetails")
    @Description("根据预定编号和乘客姓名查询机票预定的详细信息，包括航班日期、状态、舱位等")
    public Function<BookingDetailRequest, Booking> queryFlightBookingDetails() {
        return bookingDetailRequest -> flightBookingService.getBooking(bookingDetailRequest.bookingNumber, bookingDetailRequest.name);
    }

    @Bean(name = "modifyFlightBooking")
    @Description("修改已有机票预定的日期、出发地或目的地，需要提供预定编号、乘客姓名以及新的行程信息")
    public Function<ChangeBookingDetailRequest, String> modifyFlightBooking() {
        return changeBookingDatesRequest -> {
            flightBookingService.changeBooking(changeBookingDatesRequest.bookingNumber, changeBookingDatesRequest.name, changeBookingDatesRequest.newDate, changeBookingDatesRequest.from, changeBookingDatesRequest.to);
            return "success";
        };
    }

    @Bean(name = "cancelFlightBooking")
    @Description("取消指定的机票预定，需要提供预定编号和乘客姓名，取消后不可恢复")
    public Function<CancelBookingRequest, String> cancelFlightBooking() {
        return cancelBookingRequest -> {
            flightBookingService.cancelBooking(cancelBookingRequest.bookingNumber, cancelBookingRequest.name);
            return "success";
        };
    }

}
