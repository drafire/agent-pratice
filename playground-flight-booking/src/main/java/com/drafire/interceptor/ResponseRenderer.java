package com.drafire.interceptor;

import com.drafire.data.Booking;
import com.drafire.data.BookingClass;
import com.drafire.data.BookingStatus;
import com.drafire.data.Flight;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ResponseRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public String renderFlightList(List<Flight> flights, String fromCity, String toCity) {
        if (flights == null || flights.isEmpty()) {
            return "您好，暂时没有找到" + fromCity + "到" + toCity + "的航班信息。"
                    + "请直接输出以上内容，不要添加任何解释、建议、替代方案或其他信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(fromCity).append("到").append(toCity).append("的航班信息如下：\n");
        for (int i = 0; i < flights.size(); i++) {
            Flight f = flights.get(i);
            sb.append(i + 1).append(". ");
            sb.append(f.getFlightNumber()).append(" ");
            sb.append(f.getDepartureAirport()).append("→").append(f.getArrivalAirport()).append(" ");
            sb.append(f.getFlightDate()).append(" ");
            sb.append(f.getDepartureTime() != null ? f.getDepartureTime().format(TIME_FMT) : "--").append("-");
            sb.append(f.getArrivalTime() != null ? f.getArrivalTime().format(TIME_FMT) : "--").append(" ");
            sb.append("¥").append(f.getPrice()).append(" ");
            sb.append("余座").append(f.getAvailableSeats());
            if (i < flights.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String renderBooking(Booking booking) {
        if (booking == null) {
            return "您好，未找到该预订信息，请确认预订号和姓名是否正确。"
                    + "请直接输出以上内容，不要添加任何解释、建议、替代方案或其他信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("预订详情如下：\n");
        sb.append("预订编号：").append(booking.getBookingNumber()).append("\n");
        sb.append("乘客姓名：").append(booking.getCustomerName() != null ? booking.getCustomerName() : "").append("\n");
        sb.append("出发地：").append(booking.getOrigin()).append("\n");
        sb.append("目的地：").append(booking.getDestination()).append("\n");
        sb.append("出发日期：").append(booking.getDate() != null ? booking.getDate().format(DATE_FMT) : "").append("\n");
        if (booking.getBookingTo() != null) {
            sb.append("回程日期：").append(booking.getBookingTo().format(DATE_FMT)).append("\n");
        }
        sb.append("状态：").append(renderStatus(booking.getBookingStatus())).append("\n");
        sb.append("舱位：").append(renderClass(booking.getBookingClass()));
        return sb.toString();
    }

    public String renderModifySuccess(String bookingNumber, String from, String to, LocalDate newDate) {
        return "您好，已成功将预订" + bookingNumber + "的行程修改为" + from + "→" + to
                + "，日期" + (newDate != null ? newDate.format(DATE_FMT) : "") + "。";
    }

    public String renderCancelSuccess(String bookingNumber) {
        return "已成功取消预订" + bookingNumber + "。";
    }


    public String renderError() {
        return "您好，暂时无法处理您的请求，请稍后再试。"
                + "请直接输出以上内容，不要添加任何解释、建议、替代方案或其他信息。";
    }

    private String renderStatus(BookingStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case CONFIRMED -> "已确认";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
        };
    }

    private String renderClass(BookingClass bookingClass) {
        if (bookingClass == null) {
            return "";
        }
        return switch (bookingClass) {
            case ECONOMY -> "经济舱";
            case PREMIUM_ECONOMY -> "超级经济舱";
            case BUSINESS -> "商务舱";
        };
    }
}