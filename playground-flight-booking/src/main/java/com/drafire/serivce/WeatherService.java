package com.drafire.serivce;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private static final Map<String, String> CITY_LOCATION_MAP = new ConcurrentHashMap<>();

    static {
        CITY_LOCATION_MAP.put("北京", "101010100");
        CITY_LOCATION_MAP.put("beijing", "101010100");
        CITY_LOCATION_MAP.put("上海", "101020100");
        CITY_LOCATION_MAP.put("shanghai", "101020100");
        CITY_LOCATION_MAP.put("广州", "101280101");
        CITY_LOCATION_MAP.put("guangzhou", "101280101");
        CITY_LOCATION_MAP.put("深圳", "101280601");
        CITY_LOCATION_MAP.put("shenzhen", "101280601");
        CITY_LOCATION_MAP.put("杭州", "101210101");
        CITY_LOCATION_MAP.put("hangzhou", "101210101");
        CITY_LOCATION_MAP.put("成都", "101270101");
        CITY_LOCATION_MAP.put("chengdu", "101270101");
        CITY_LOCATION_MAP.put("南京", "101190101");
        CITY_LOCATION_MAP.put("nanjing", "101190101");
        CITY_LOCATION_MAP.put("武汉", "101200101");
        CITY_LOCATION_MAP.put("wuhan", "101200101");
        CITY_LOCATION_MAP.put("重庆", "101040100");
        CITY_LOCATION_MAP.put("chongqing", "101040100");
        CITY_LOCATION_MAP.put("西安", "101110101");
        CITY_LOCATION_MAP.put("xian", "101110101");
        CITY_LOCATION_MAP.put("昆明", "101290101");
        CITY_LOCATION_MAP.put("kunming", "101290101");
        CITY_LOCATION_MAP.put("厦门", "101230201");
        CITY_LOCATION_MAP.put("xiamen", "101230201");
        CITY_LOCATION_MAP.put("天津", "101030100");
        CITY_LOCATION_MAP.put("tianjin", "101030100");
        CITY_LOCATION_MAP.put("长沙", "101250101");
        CITY_LOCATION_MAP.put("changsha", "101250101");
        CITY_LOCATION_MAP.put("青岛", "101120201");
        CITY_LOCATION_MAP.put("qingdao", "101120201");
        CITY_LOCATION_MAP.put("三亚", "101310201");
        CITY_LOCATION_MAP.put("sanya", "101310201");
        CITY_LOCATION_MAP.put("大连", "101070201");
        CITY_LOCATION_MAP.put("dalian", "101070201");
        CITY_LOCATION_MAP.put("哈尔滨", "101050101");
        CITY_LOCATION_MAP.put("haerbin", "101050101");
        CITY_LOCATION_MAP.put("harbin", "101050101");
        CITY_LOCATION_MAP.put("苏州", "101190401");
        CITY_LOCATION_MAP.put("suzhou", "101190401");
        CITY_LOCATION_MAP.put("郑州", "101180101");
        CITY_LOCATION_MAP.put("zhengzhou", "101180101");
        CITY_LOCATION_MAP.put("济南", "101120101");
        CITY_LOCATION_MAP.put("jinan", "101120101");
        CITY_LOCATION_MAP.put("福州", "101230101");
        CITY_LOCATION_MAP.put("fuzhou", "101230101");
        CITY_LOCATION_MAP.put("贵阳", "101260101");
        CITY_LOCATION_MAP.put("guiyang", "101260101");
        CITY_LOCATION_MAP.put("海口", "101310101");
        CITY_LOCATION_MAP.put("haikou", "101310101");
        CITY_LOCATION_MAP.put("拉萨", "101140101");
        CITY_LOCATION_MAP.put("lasa", "101140101");
        CITY_LOCATION_MAP.put("lhasa", "101140101");
        CITY_LOCATION_MAP.put("乌鲁木齐", "101130101");
        CITY_LOCATION_MAP.put("wulumuqi", "101130101");
        CITY_LOCATION_MAP.put("urumqi", "101130101");
        CITY_LOCATION_MAP.put("桂林", "101300501");
        CITY_LOCATION_MAP.put("guilin", "101300501");
        CITY_LOCATION_MAP.put("台北", "101340101");
        CITY_LOCATION_MAP.put("taipei", "101340101");
        CITY_LOCATION_MAP.put("香港", "101320101");
        CITY_LOCATION_MAP.put("hongkong", "101320101");
        CITY_LOCATION_MAP.put("澳门", "101330101");
        CITY_LOCATION_MAP.put("macau", "101330101");
        CITY_LOCATION_MAP.put("macao", "101330101");
    }

    private final RestClient restClient;
    private final String apiKey;

    public WeatherService(@Value("${spring.ai.alibaba.toolcalling.hefeng.api-key}") String apiKey,
                         @Value("${spring.ai.alibaba.toolcalling.hefeng.base-url:https://devapi.qweather.com}") String baseUrl) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String getWeather(String city, int days) {
        logger.info("查询天气: city={}, days={}", city, days);
        if (days == 0) {
            return getCurrentWeather(city);
        }
        return getForecastWeather(city, days);
    }

    private String resolveLocationId(String city) {
        if (CITY_LOCATION_MAP.containsKey(city)) {
            return CITY_LOCATION_MAP.get(city);
        }
        String lowerCity = city.toLowerCase().trim();
        if (CITY_LOCATION_MAP.containsKey(lowerCity)) {
            return CITY_LOCATION_MAP.get(lowerCity);
        }
        String locationId = lookupCityLocation(city);
        if (locationId != null) {
            CITY_LOCATION_MAP.put(city, locationId);
            return locationId;
        }
        return city;
    }

    private String lookupCityLocation(String city) {
        try {
            logger.info("通过API查询城市Location ID: {}", city);
            HefengCityLookupResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/city/lookup")
                            .queryParam("location", city)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(HefengCityLookupResponse.class);

            if (response != null && "200".equals(response.code)
                    && response.location != null && !response.location.isEmpty()) {
                String id = response.location.get(0).id;
                logger.info("城市 {} 的Location ID: {}", city, id);
                return id;
            }
        } catch (Exception e) {
            logger.warn("查询城市Location ID失败: {}", e.getMessage());
        }
        return null;
    }

    private String getCurrentWeather(String city) {
        try {
            String locationId = resolveLocationId(city);
            HefengNowResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v7/weather/now")
                            .queryParam("location", locationId)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(HefengNowResponse.class);

            if (response == null || response.code == null || !"200".equals(response.code)) {
                return "未查询到" + city + "的天气信息";
            }

            NowWeather now = response.now;
            if (now == null) {
                return "未查询到" + city + "的天气信息";
            }
            return city + "当前天气：" + now.text + "，温度" + now.temp + "°C，"
                    + now.windDir + now.windScale + "级，湿度" + now.humidity + "%";
        } catch (Exception e) {
            logger.error("查询天气失败: {}", e.getMessage());
            return "暂时无法查询" + city + "的天气，请稍后再试。";
        }
    }

    private String getForecastWeather(String city, int days) {
        try {
            String locationId = resolveLocationId(city);
            HefengForecastResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v7/weather/forecast")
                            .queryParam("location", locationId)
                            .queryParam("key", apiKey)
                            .queryParam("days", days)
                            .build())
                    .retrieve()
                    .body(HefengForecastResponse.class);

            if (response == null || response.code == null || !"200".equals(response.code)
                    || response.daily == null || response.daily.isEmpty()) {
                return "未查询到" + city + "的天气预报信息";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("未来").append(response.daily.size()).append("天").append(city).append("的天气：");
            for (DailyForecast daily : response.daily) {
                sb.append("\n").append(daily.fxDate).append("（").append(getWeekDay(daily.fxDate)).append("）：")
                        .append("白天").append(daily.dayText).append(" ").append(daily.dayTemp).append("°C，")
                        .append("夜间").append(daily.nightText).append(" ").append(daily.nightTemp).append("°C，")
                        .append(daily.windDir).append(daily.windScale).append("级");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("查询天气预报失败: {}", e.getMessage());
            return "暂时无法查询" + city + "的天气预报，请稍后再试。";
        }
    }

    private String getWeekDay(String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            java.time.DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            String[] weekDays = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            return weekDays[dayOfWeek.getValue() - 1];
        } catch (Exception e) {
            return "";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HefengNowResponse {
        public String code;
        public NowWeather now;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NowWeather {
        public String temp;
        public String text;
        public String windDir;
        public String windScale;
        public String humidity;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HefengForecastResponse {
        public String code;
        public java.util.List<DailyForecast> daily;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyForecast {
        public String fxDate;
        @JsonProperty("tempMax")
        public String dayTemp;
        @JsonProperty("tempMin")
        public String nightTemp;
        @JsonProperty("textDay")
        public String dayText;
        @JsonProperty("textNight")
        public String nightText;
        public String windDir;
        public String windScale;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HefengCityLookupResponse {
        public String code;
        public java.util.List<CityLocation> location;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CityLocation {
        public String id;
        public String name;
        public String country;
        public String adm1;
        public String adm2;
    }
}