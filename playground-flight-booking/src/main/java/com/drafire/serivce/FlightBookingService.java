package com.drafire.serivce;

import com.drafire.data.Booking;
import com.drafire.data.BookingMapper;
import com.drafire.data.BookingStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class FlightBookingService {

    private final BookingMapper bookingMapper;

    public FlightBookingService(BookingMapper bookingMapper) {
        this.bookingMapper = bookingMapper;
    }

    public Booking getBooking(String bookingNumber, String name) {
        Booking booking = bookingMapper.findByBookingNumberAndCustomerName(bookingNumber, name);
        if (booking == null) {
            throw new IllegalArgumentException("Booking not found with number: " + bookingNumber + " and name: " + name);
        }
        return booking;
    }

    public void changeBooking(String bookingNumber, String name, LocalDate newDate, String from, String to) {
        Booking booking = getBooking(bookingNumber, name);

        if (newDate.isBefore(booking.getDate())) {
            throw new IllegalArgumentException("New date must be after old date");
        }

        if (newDate.isBefore(LocalDate.now().plusDays(1))) {
            throw new IllegalArgumentException("Booking cannot be changed within 24 hours of the start date.");
        }

        int updated = bookingMapper.updateBooking(bookingNumber, name, newDate, from, to);
        if (updated == 0) {
            throw new IllegalStateException("Failed to update booking: " + bookingNumber);
        }
    }

    public void cancelBooking(String bookingNumber, String name) {
        Booking booking = getBooking(bookingNumber, name);

        if (booking.getDate().isBefore(LocalDate.now().plusDays(2))) {
            throw new IllegalArgumentException("Booking cannot be cancelled within 48 hours of the start date.");
        }

        int updated = bookingMapper.updateBookingStatus(bookingNumber, name, BookingStatus.CANCELLED.name());
        if (updated == 0) {
            throw new IllegalStateException("Failed to cancel booking: " + bookingNumber);
        }
    }
}