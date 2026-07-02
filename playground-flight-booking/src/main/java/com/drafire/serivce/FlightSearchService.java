package com.drafire.serivce;

import com.drafire.data.Flight;
import com.drafire.data.FlightMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FlightSearchService {

    private final FlightMapper flightMapper;

    public FlightSearchService(FlightMapper flightMapper) {
        this.flightMapper = flightMapper;
    }

    public List<Flight> queryFlightsBetweenTwoCities(String fromCity, String toCity) {
        List<Flight> flightList = flightMapper.findByDepartureCityAndArrivalCity(fromCity, toCity);
        return flightList;
    }
}