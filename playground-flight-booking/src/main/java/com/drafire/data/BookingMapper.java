package com.drafire.data;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface BookingMapper {

    Booking findByBookingNumberAndCustomerName(@Param("bookingNumber") String bookingNumber,
                                               @Param("customerName") String customerName);

    int updateBooking(@Param("bookingNumber") String bookingNumber,
                      @Param("customerName") String customerName,
                      @Param("newDate") LocalDate newDate,
                      @Param("origin") String origin,
                      @Param("destination") String destination);

    int updateBookingStatus(@Param("bookingNumber") String bookingNumber,
                            @Param("customerName") String customerName,
                            @Param("status") String status);

    int insertBooking(Booking booking);
}