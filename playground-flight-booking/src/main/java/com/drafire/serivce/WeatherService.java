package com.drafire.serivce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient restClient;
    private final String apiKey;

    public WeatherService(RestClient.Builder restClientBuilder,
                          @Value("${spring.ai.alibaba.toolcalling.amap.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = restClientBuilder
                .baseUrl("https://restapi.amap.com")
                .build();
    }

    public String getWeather(String city, int days) {
        logger.info("查询天气: city={}, days={}", city, days);
        if (days == 0) {
            return getCurrentWeather(city);
        }
        return getForecastWeather(city, days);
    }

    private String getCurrentWeather(String city) {
        try {
            WeatherResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/weather/weatherInfo")
                            .queryParam("city", city)
                            .queryParam("key", apiKey)
                            .queryParam("extensions", "base")
                            .build())
                    .retrieve()
                    .body(WeatherResponse.class);

            if (response == null || response.lives == null || response.lives.isEmpty()) {
                return "未查询到" + city + "的天气信息";
            }

            LiveWeather live = response.lives.get(0);
            return city + "当前天气：天气" + live.weather + "，温度" + live.temperature + "°C，"
                    + live.winddirection + "风" + live.windpower + "级，湿度" + live.humidity + "%";
        } catch (Exception e) {
            logger.error("查询天气失败: {}", e.getMessage());
            return "查询" + city + "的天气失败：" + e.getMessage();
        }
    }

    private String getForecastWeather(String city, int days) {
        try {
            ForecastResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/weather/weatherInfo")
                            .queryParam("city", city)
                            .queryParam("key", apiKey)
                            .queryParam("days", days)
                            .queryParam("extensions", "all")
                            .build())
                    .retrieve()
                    .body(ForecastResponse.class);

            if (response == null || response.forecasts == null || response.forecasts.isEmpty()) {
                return "未查询到" + city + "的天气预报信息";
            }

            Forecast forecast = response.forecasts.get(0);
            if (forecast.casts == null || forecast.casts.isEmpty()) {
                return "未查询到" + city + "的天气预报信息";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("未来").append(forecast.casts.size()).append("天").append(city).append("的天气：");
            for (Cast cast : forecast.casts) {
                sb.append("\n").append(cast.date).append("（").append(cast.week).append("）：")
                        .append("白天").append(cast.dayweather).append(" ").append(cast.daytemp).append("°C，")
                        .append("夜间").append(cast.nightweather).append(" ").append(cast.nighttemp).append("°C，")
                        .append(cast.daywind).append("风").append(cast.daypower).append("级");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("查询天气预报失败: {}", e.getMessage());
            return "查询" + city + "的天气预报失败：" + e.getMessage();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeatherResponse {
        public String status;
        public List<LiveWeather> lives;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LiveWeather {
        public String province;
        public String city;
        public String weather;
        public String temperature;
        public String winddirection;
        public String windpower;
        public String humidity;
        public String reporttime;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ForecastResponse {
        public String status;
        public List<Forecast> forecasts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Forecast {
        public String city;
        public String province;
        public List<Cast> casts;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cast {
        public String date;
        public String week;
        @JsonProperty("dayweather")
        public String dayweather;
        @JsonProperty("nightweather")
        public String nightweather;
        @JsonProperty("daytemp")
        public String daytemp;
        @JsonProperty("nighttemp")
        public String nighttemp;
        @JsonProperty("daywind")
        public String daywind;
        @JsonProperty("nightwind")
        public String nightwind;
        @JsonProperty("daypower")
        public String daypower;
        @JsonProperty("nightpower")
        public String nightpower;
    }
}