package com.drafire.serivce;

import com.drafire.data.Booking;
import com.drafire.data.BookingMapper;
import com.drafire.data.BookingStatus;
import com.drafire.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FlightBookingService {

    private final BookingMapper bookingMapper;

    public FlightBookingService(BookingMapper bookingMapper) {
        this.bookingMapper = bookingMapper;
    }

    public Booking getBooking(String bookingNumber, String name) {
        Booking booking = bookingMapper.findByBookingNumberAndCustomerName(bookingNumber, name);
        if (booking == null) {
            throw new BusinessException("BOOKING_NOT_FOUND",
                    "未找到订单 " + bookingNumber + "，请检查订单号和姓名");
        }
        return booking;
    }

    public void changeBooking(String bookingNumber, String name, LocalDate newDate, String from, String to) {
        Booking booking = getBooking(bookingNumber, name);

        if (booking.getDate() == null) {
            throw new BusinessException("DATE_MISSING", "订单 " + bookingNumber + " 缺少出发日期，无法改签");
        }

        if (newDate.isBefore(booking.getDate())) {
            throw new BusinessException("DATE_INVALID", "新日期必须晚于当前出发日期 " + booking.getDate());
        }

        if (newDate.isBefore(LocalDate.now().plusDays(1))) {
            throw new BusinessException("DATE_TOO_SOON", "出发前 24 小时内不可改签");
        }

        int updated = bookingMapper.updateBooking(bookingNumber, name, newDate, from, to);
        if (updated == 0) {
            throw new BusinessException("UPDATE_FAILED", "改签失败，请稍后重试");
        }
    }

    public void cancelBooking(String bookingNumber, String name) {
        Booking booking = getBooking(bookingNumber, name);

        if (booking.getDate() == null) {
            throw new BusinessException("DATE_MISSING", "订单 " + bookingNumber + " 缺少出发日期，无法取消");
        }

        if (booking.getDate().isBefore(LocalDate.now().plusDays(2))) {
            throw new BusinessException("CANCEL_TOO_SOON", "出发前 48 小时内不可取消订单");
        }

        int updated = bookingMapper.updateBookingStatus(bookingNumber, name, BookingStatus.CANCELLED.name());
        if (updated == 0) {
            throw new BusinessException("CANCEL_FAILED", "取消订单失败，请稍后重试");
        }
    }

    public List<Booking> findAll() {
        return bookingMapper.findAll();
    }
}