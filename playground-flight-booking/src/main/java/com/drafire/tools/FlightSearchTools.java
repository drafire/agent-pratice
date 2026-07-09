package com.drafire.tools;

import com.drafire.data.Flight;
import com.drafire.serivce.FlightSearchService;
import com.drafire.interceptor.ResponseRenderer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Configuration
public class FlightSearchTools {

    private final FlightSearchService flightSearchService;
    private final ResponseRenderer responseRenderer;

    public FlightSearchTools(FlightSearchService flightSearchService, ResponseRenderer responseRenderer) {
        this.flightSearchService = flightSearchService;
        this.responseRenderer = responseRenderer;
    }

    public record FlightSearchRequest(String fromCity, String toCity, String date) {
    }

    @Bean(name = "queryFlightsBetweenTwoCities")
    @Description("查询两个城市之间是否有航班以及航班详细信息。当用户询问任何两个城市之间是否有航班时，必须调用此函数获取真实数据，不能凭常识判断")
    @ToolFunction
    public Function<FlightSearchRequest, String> queryFlightsBetweenTwoCities() {
        return flightSearchRequest -> {
            String fromCity = flightSearchRequest.fromCity();
            String toCity = flightSearchRequest.toCity();
            if (fromCity == null || fromCity.isBlank() || toCity == null || toCity.isBlank()) {
                return "您好，请提供出发城市和到达城市。"
                        + "请直接输出以上内容，不要添加任何解释、建议、替代方案或其他信息。";
            }
            LocalDate flightDate = null;
            if (flightSearchRequest.date() != null && !flightSearchRequest.date().isEmpty()) {
                try {
                    flightDate = LocalDate.parse(flightSearchRequest.date());
                } catch (Exception e) {
                    // 日期解析失败时忽略日期过滤
                }
            }
            List<Flight> flights = flightDate != null
                    ? flightSearchService.queryFlightsBetweenTwoCities(
                            flightSearchRequest.fromCity(), flightSearchRequest.toCity(), flightDate)
                    : flightSearchService.queryFlightsBetweenTwoCities(
                            flightSearchRequest.fromCity(), flightSearchRequest.toCity());
            return responseRenderer.renderFlightList(flights,
                    flightSearchRequest.fromCity(), flightSearchRequest.toCity());
        };
    }
}