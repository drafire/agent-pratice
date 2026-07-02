package com.drafire.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FlightDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FlightDataInitializer.class);

    private final FlightMapper flightMapper;

    public FlightDataInitializer(FlightMapper flightMapper) {
        this.flightMapper = flightMapper;
    }

    @Override
    public void run(String... args) {
        int count = flightMapper.countAll();
        if (count > 0) {
            log.info("Flights table already contains {} records, skipping initialization.", count);
            return;
        }

        log.info("Initializing flight data...");
        List<Flight> flights = generateFlights();
        log.info("Generated {} flights, inserting into database...", flights.size());

        int batchSize = 200;
        for (int i = 0; i < flights.size(); i += batchSize) {
            int end = Math.min(i + batchSize, flights.size());
            flightMapper.insertBatch(flights.subList(i, end));
            log.info("Inserted batch {}/{}", (i / batchSize) + 1, (flights.size() + batchSize - 1) / batchSize);
        }

        log.info("Flight data initialization complete. Total records: {}", flightMapper.countAll());
    }

    private List<Flight> generateFlights() {
        List<Flight> flights = new ArrayList<>();
        Random rng = ThreadLocalRandom.current();

        List<City> cities = buildCities();
        List<Airline> airlines = buildAirlines();
        List<Route> routes = buildRoutes(cities);
        List<String> aircraftTypes = buildAircraftTypes();
        LocalTime[] timeSlots = buildTimeSlots();

        LocalDate startDate = LocalDate.now().plusDays(1);
        int dateRange = 7;

        java.util.Map<String, Integer> airlineCounters = new java.util.HashMap<>();
        for (Airline a : airlines) {
            airlineCounters.put(a.code, 1000);
        }

        for (Route route : routes) {
            int flightBasePrice = route.basePrice;

            List<Airline> assignedAirlines = pickAirlinesForRoute(airlines, route, rng);

            for (Airline airline : assignedAirlines) {
                String aircraftType = aircraftTypes.get(rng.nextInt(aircraftTypes.size()));

                for (LocalTime slot : timeSlots) {
                    if (rng.nextDouble() < 0.3) {
                        continue;
                    }

                    int duration = route.duration + rng.nextInt(21) - 10;
                    if (duration < 60) duration = 60;
                    LocalTime arrival = slot.plusMinutes(duration);

                    for (int d = 0; d < dateRange; d++) {
                        if (rng.nextDouble() < 0.25) {
                            continue;
                        }

                        LocalDate flightDate = startDate.plusDays(d);

                        BigDecimal price = BigDecimal.valueOf(flightBasePrice)
                                .multiply(airline.priceFactor)
                                .add(BigDecimal.valueOf(rng.nextInt(201) - 100))
                                .setScale(0, RoundingMode.HALF_UP);

                        if (price.compareTo(BigDecimal.valueOf(200)) < 0) {
                            price = BigDecimal.valueOf(200);
                        }

                        int seats = 120 + rng.nextInt(81);

                        int counter = airlineCounters.get(airline.code);
                        String flightNum = airline.code + counter;
                        airlineCounters.put(airline.code, counter + 1);

                        Flight flight = new Flight(
                                flightNum,
                                airline.name,
                                route.departureCity.name,
                                route.arrivalCity.name,
                                route.departureCity.airportCode,
                                route.arrivalCity.airportCode,
                                slot,
                                arrival,
                                duration,
                                aircraftType,
                                price,
                                seats,
                                flightDate
                        );

                        flights.add(flight);
                    }
                }
            }
        }

        return flights;
    }

    private List<Airline> pickAirlinesForRoute(List<Airline> airlines, Route route, Random rng) {
        List<Airline> result = new ArrayList<>();
        int count = route.isMajor ? 3 + rng.nextInt(3) : 1 + rng.nextInt(2);
        List<Airline> shuffled = new ArrayList<>(airlines);
        java.util.Collections.shuffle(shuffled, rng);
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            result.add(shuffled.get(i));
        }
        return result;
    }

    private List<City> buildCities() {
        List<City> cities = new ArrayList<>();
        cities.add(new City("北京", "PEK"));
        cities.add(new City("北京", "PKX"));
        cities.add(new City("上海", "PVG"));
        cities.add(new City("上海", "SHA"));
        cities.add(new City("广州", "CAN"));
        cities.add(new City("深圳", "SZX"));
        cities.add(new City("成都", "CTU"));
        cities.add(new City("成都", "TFU"));
        cities.add(new City("杭州", "HGH"));
        cities.add(new City("重庆", "CKG"));
        cities.add(new City("西安", "XIY"));
        cities.add(new City("昆明", "KMG"));
        cities.add(new City("南京", "NKG"));
        cities.add(new City("武汉", "WUH"));
        cities.add(new City("长沙", "CSX"));
        cities.add(new City("厦门", "XMN"));
        cities.add(new City("青岛", "TAO"));
        cities.add(new City("大连", "DLC"));
        cities.add(new City("三亚", "SYX"));
        cities.add(new City("海口", "HAK"));
        cities.add(new City("哈尔滨", "HRB"));
        cities.add(new City("沈阳", "SHE"));
        cities.add(new City("郑州", "CGO"));
        cities.add(new City("乌鲁木齐", "URC"));
        cities.add(new City("贵阳", "KWE"));
        cities.add(new City("福州", "FOC"));
        cities.add(new City("南宁", "NNG"));
        cities.add(new City("合肥", "HFE"));
        cities.add(new City("呼和浩特", "HET"));
        cities.add(new City("兰州", "LHW"));
        cities.add(new City("银川", "INC"));
        cities.add(new City("西宁", "XNN"));
        cities.add(new City("拉萨", "LXA"));
        cities.add(new City("天津", "TSN"));
        cities.add(new City("南昌", "KHN"));
        cities.add(new City("石家庄", "SJW"));
        cities.add(new City("太原", "TYN"));
        cities.add(new City("长春", "CGQ"));
        cities.add(new City("济南", "TNA"));
        return cities;
    }

    private List<Airline> buildAirlines() {
        List<Airline> airlines = new ArrayList<>();
        airlines.add(new Airline("CA", "中国国航", BigDecimal.valueOf(1.05)));
        airlines.add(new Airline("MU", "东方航空", BigDecimal.valueOf(1.00)));
        airlines.add(new Airline("CZ", "南方航空", BigDecimal.valueOf(1.02)));
        airlines.add(new Airline("HU", "海南航空", BigDecimal.valueOf(0.95)));
        airlines.add(new Airline("3U", "四川航空", BigDecimal.valueOf(0.92)));
        airlines.add(new Airline("ZH", "深圳航空", BigDecimal.valueOf(0.98)));
        airlines.add(new Airline("MF", "厦门航空", BigDecimal.valueOf(0.96)));
        airlines.add(new Airline("FM", "上海航空", BigDecimal.valueOf(1.00)));
        airlines.add(new Airline("SC", "山东航空", BigDecimal.valueOf(0.93)));
        airlines.add(new Airline("9C", "春秋航空", BigDecimal.valueOf(0.80)));
        return airlines;
    }

    private List<Route> buildRoutes(List<City> cities) {
        List<Route> routes = new ArrayList<>();

        int[][] cityIndices = {
                {0, 4}, {0, 5}, {0, 8}, {0, 10}, {0, 12}, {0, 14}, {0, 16}, {0, 18}, {0, 20},
                {0, 22}, {0, 24}, {0, 26}, {0, 28}, {0, 32}, {0, 34},
                {4, 5}, {4, 8}, {4, 10}, {4, 12}, {4, 14}, {4, 16}, {4, 18}, {4, 20},
                {4, 22}, {4, 26}, {4, 28}, {4, 32},
                {8, 5}, {8, 10}, {8, 12}, {8, 14}, {8, 16}, {8, 18}, {8, 20}, {8, 22},
                {8, 24}, {8, 26}, {8, 28}, {8, 30}, {8, 32},
                {10, 5}, {10, 8}, {10, 12}, {10, 14}, {10, 16}, {10, 18}, {10, 20},
                {10, 22}, {10, 24}, {10, 26}, {10, 28}, {10, 30}, {10, 32},
                {12, 5}, {12, 8}, {12, 10}, {12, 14}, {12, 16}, {12, 18}, {12, 20},
                {12, 22}, {12, 24}, {12, 26}, {12, 32},
                {16, 5}, {16, 8}, {16, 10}, {16, 14}, {16, 18}, {16, 20}, {16, 22}, {16, 24},
                {16, 26}, {16, 28}, {16, 32},
                {18, 5}, {18, 8}, {18, 10}, {18, 14}, {18, 16}, {18, 20}, {18, 22},
                {18, 24}, {18, 26}, {18, 28}, {18, 32},
                {22, 4}, {22, 5}, {22, 8}, {22, 10}, {22, 12}, {22, 14}, {22, 16},
                {22, 18}, {22, 20}, {22, 24}, {22, 26}, {22, 28}, {22, 32},
                {24, 4}, {24, 5}, {24, 8}, {24, 10}, {24, 12}, {24, 14}, {24, 16},
                {24, 18}, {24, 20}, {24, 22}, {24, 26}, {24, 28}, {24, 30}, {24, 32},
                {26, 4}, {26, 5}, {26, 8}, {26, 10}, {26, 12}, {26, 14}, {26, 16},
                {26, 18}, {26, 20}, {26, 22}, {26, 24}, {26, 28}, {26, 32},
                {28, 4}, {28, 5}, {28, 8}, {28, 10}, {28, 12}, {28, 14}, {28, 16},
                {28, 18}, {28, 20}, {28, 22}, {28, 24}, {28, 26}, {28, 32},
                {30, 4}, {30, 5}, {30, 8}, {30, 10}, {30, 12}, {30, 14},
                {30, 18}, {30, 20}, {30, 22}, {30, 24}, {30, 28}, {30, 32},
                {32, 4}, {32, 5}, {32, 8}, {32, 10}, {32, 12}, {32, 14},
                {32, 16}, {32, 18}, {32, 20}, {32, 22}, {32, 24}, {32, 26}, {32, 28},
                {34, 4}, {34, 5}, {34, 8}, {34, 10}, {34, 12}, {34, 14},
                {34, 16}, {34, 18}, {34, 20}, {34, 22}, {34, 24}, {34, 26}, {34, 32},
                {36, 4}, {36, 5}, {36, 8}, {36, 10}, {36, 12}, {36, 14},
                {36, 18}, {36, 20}, {36, 24}, {36, 32},
                {38, 4}, {38, 5}, {38, 8}, {38, 10}, {38, 12}, {38, 14},
                {38, 16}, {38, 18}, {38, 20}, {38, 22}, {38, 24}, {38, 32},
        };

        int[][] durations = {
                {195, 180, 150, 150, 195, 135, 105, 60, 120, 150, 210, 150, 150, 90, 120, 180, 210, 240, 270, 300},
                {150, 135, 135, 135, 105, 75, 90, 90, 135, 90, 120, 90, 90, 90, 120, 150, 180, 210, 240, 270},
                {120, 90, 90, 90, 90, 105, 105, 90, 90, 90, 120, 120, 90, 120, 90, 120, 150, 180, 210, 240},
                {120, 90, 90, 90, 150, 90, 90, 90, 75, 120, 150, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 150, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 60, 90, 60, 90, 90, 90, 60, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 60, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 60, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 60, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 60, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
                {90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 90, 120, 150, 180, 210, 240},
        };

        int[][] basePrices = {
                {1200, 1400, 900, 1200, 1500, 1100, 800, 500, 1000, 1300, 1800, 1200, 1200, 700, 900, 1500, 1800, 2200, 2500, 3000},
                {1000, 1100, 1000, 1100, 800, 600, 700, 700, 1100, 700, 1000, 700, 700, 700, 900, 1300, 1500, 1800, 2200, 2500},
                {900, 700, 700, 700, 700, 800, 800, 700, 700, 700, 900, 900, 700, 900, 700, 1000, 1300, 1600, 2000, 2300},
                {900, 700, 700, 700, 1200, 700, 700, 700, 600, 900, 1200, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 1200, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 500, 700, 500, 700, 700, 700, 500, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 500, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 500, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 500, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 500, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
                {700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 700, 1000, 1300, 1600, 2000, 2300},
        };

        for (int[] pair : cityIndices) {
            City from = cities.get(pair[0]);
            City to = cities.get(pair[1]);

            int fromGroup = getCityGroup(pair[0]);
            int toGroup = getCityGroup(pair[1]);
            int duration = durations[fromGroup][toGroup];
            int basePrice = basePrices[fromGroup][toGroup];
            boolean major = (fromGroup <= 3 || toGroup <= 3);

            routes.add(new Route(from, to, duration, basePrice, major));
        }

        return routes;
    }

    private int getCityGroup(int cityIndex) {
        if (cityIndex <= 1) return 0;
        if (cityIndex <= 3) return 1;
        if (cityIndex <= 7) return 2;
        if (cityIndex <= 9) return 3;
        if (cityIndex == 10 || cityIndex == 11) return 4;
        if (cityIndex == 12 || cityIndex == 13) return 5;
        if (cityIndex == 14 || cityIndex == 15) return 6;
        if (cityIndex == 16 || cityIndex == 17) return 7;
        if (cityIndex == 18 || cityIndex == 19) return 8;
        if (cityIndex == 20 || cityIndex == 21) return 9;
        if (cityIndex == 22 || cityIndex == 23) return 10;
        if (cityIndex == 24 || cityIndex == 25) return 11;
        if (cityIndex == 26 || cityIndex == 27) return 12;
        if (cityIndex == 28 || cityIndex == 29) return 13;
        if (cityIndex == 30 || cityIndex == 31) return 14;
        if (cityIndex == 32 || cityIndex == 33) return 15;
        if (cityIndex == 34 || cityIndex == 35) return 16;
        if (cityIndex == 36 || cityIndex == 37) return 17;
        return 18;
    }

    private List<String> buildAircraftTypes() {
        List<String> types = new ArrayList<>();
        types.add("Boeing 737-800");
        types.add("Boeing 737 MAX 8");
        types.add("Boeing 787-9");
        types.add("Airbus A320");
        types.add("Airbus A321");
        types.add("Airbus A330-300");
        types.add("Airbus A350-900");
        types.add("COMAC C919");
        return types;
    }

    private LocalTime[] buildTimeSlots() {
        return new LocalTime[]{
                LocalTime.of(7, 0),
                LocalTime.of(8, 30),
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0),
        };
    }

    static class City {
        String name;
        String airportCode;

        City(String name, String airportCode) {
            this.name = name;
            this.airportCode = airportCode;
        }
    }

    static class Airline {
        String code;
        String name;
        BigDecimal priceFactor;

        Airline(String code, String name, BigDecimal priceFactor) {
            this.code = code;
            this.name = name;
            this.priceFactor = priceFactor;
        }
    }

    static class Route {
        City departureCity;
        City arrivalCity;
        int duration;
        int basePrice;
        boolean isMajor;

        Route(City departureCity, City arrivalCity, int duration, int basePrice, boolean isMajor) {
            this.departureCity = departureCity;
            this.arrivalCity = arrivalCity;
            this.duration = duration;
            this.basePrice = basePrice;
            this.isMajor = isMajor;
        }
    }
}