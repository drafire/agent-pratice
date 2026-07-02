package com.drafire.data;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FlightMapper {

    List<Flight> findByDepartureCityAndArrivalCity(@Param("departureCity") String departureCity,
                                                   @Param("arrivalCity") String arrivalCity);

    List<Flight> findByFlightNumberAndDate(@Param("flightNumber") String flightNumber,
                                           @Param("flightDate") LocalDate flightDate);

    int insertFlight(Flight flight);

    int insertBatch(@Param("flights") List<Flight> flights);

    int countAll();

    List<Flight> findAll();
}