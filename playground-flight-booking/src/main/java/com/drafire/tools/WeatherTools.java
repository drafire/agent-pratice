package com.drafire.tools;

import com.drafire.serivce.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class WeatherTools {

    private static final Logger logger = LoggerFactory.getLogger(WeatherTools.class);

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    public record WeatherRequest(String city, int days) {
    }

    @Bean("getWeatherByCity")
    @Description("根据城市获取当地的天气，days=0表示查询当前天气，days>0表示查询未来N天预报")
    public Function<WeatherRequest, String> getWeatherByCity() {
        return weatherRequest -> {
            logger.info("工具调用: getWeatherByCity, city={}, days={}", weatherRequest.city, weatherRequest.days);
            return weatherService.getWeather(weatherRequest.city, weatherRequest.days);
        };
    }
}