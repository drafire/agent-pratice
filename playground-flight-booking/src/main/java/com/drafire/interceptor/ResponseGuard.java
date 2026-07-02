package com.drafire.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResponseGuard {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGuard.class);

    private final Set<String> blocklist;

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(1[3-9]\\d{9}|400[-.]?\\d{3}[-.]?\\d{4}|\\d{3,4}[-.]?\\d{7,8})\\b");

    private static final Pattern TRAIN_PATTERN = Pattern.compile(
            "(高铁|动车|火车|列车|城际|普速|直达|特快|快速|G\\d{2,4}|D\\d{2,4}|C\\d{2,4}|K\\d{2,4}|T\\d{2,4}|Z\\d{2,4})");

    private static final Pattern TRAIN_STATION_PATTERN = Pattern.compile(
            "(杭州东|广州南|深圳北|上海虹桥|北京南|成都东|武汉站|南京南|12306|二等座|一等座|商务座|无座|扫码进站|铁路)");

    private static final Pattern COMPETITOR_AIRLINE_PATTERN = Pattern.compile(
            "(南航|国航|东航|海航|深航|川航|厦航|山航|春秋航空|吉祥航空|华夏航空|中国国际航空|中国南方航空|中国东方航空|海南航空|深圳航空|四川航空|厦门航空)");

    private static final Pattern HALLUCINATED_FLIGHT_PATTERN = Pattern.compile(
            "\\b(FM\\d{2,4}|MU\\d{2,4}|CZ\\d{2,4}|CA\\d{2,4}|3U\\d{2,4}|ZH\\d{2,4}|HU\\d{2,4}|MF\\d{2,4}|SC\\d{2,4})\\b");

    private static final Pattern HALLUCINATED_PRICE_PATTERN = Pattern.compile(
            "(最低\\s*¥|票价\\s*¥|起\\s*¥|仅需\\s*¥|只需\\s*¥|价格\\s*¥)");

    private static final Pattern HALLUCINATED_SCHEDULE_PATTERN = Pattern.compile(
            "(每日超\\s*\\d+\\s*班|每天\\s*\\d+\\s*班|最快\\d+小时|约\\d+小时\\d+分|\\d+小时\\d+分到达|\\d+分钟可达)");

    private static final Pattern IMPLEMENTATION_DETAIL_PATTERN = Pattern.compile(
            "(参数|传入|调用了|API|接口|函数名|工具名|异常类|错误码|fromCity|toCity|bookingNumber|重新查询|再次查询)");

    private static final Pattern FAKE_COOPERATION_PATTERN = Pattern.compile(
            "(深度合作|数据同步|官方合作|战略合作|代码共享|联运|联程|中转|转机|执飞|航线网络)");

    private static final Pattern EMOJI_OVERUSE_PATTERN = Pattern.compile(
            "(\\p{So}\\s*){4,}|((😊|🚄|✈️|✅|⚠️|🔹|🔸|😄|🎉|👍|💡|🔥|⭐|💯|✨|🎯|📢|💬|🤔|👋|🙏|🏆|🎊)\\s*){3,}");

    private static final Pattern OVER_VERBOSE_PATTERN = Pattern.compile(
            "(真实、高效、愉快|陪您启程|为您保驾护航|竭诚为您|全程在线|随时为您|放心|安心|省心|贴心|暖心)");

    private static final Pattern SYSTEM_PROMPT_LEAK_PATTERN = Pattern.compile(
            "[～~]?\\s*#{1,4}\\s*重要提示[\\s\\S]*$");

    private static final Pattern PROMPT_LEAK_DETECT_PATTERN = Pattern.compile(
            "#{1,4}\\s*重要提示");

    private static final Pattern EMOJI_ANY_PATTERN = Pattern.compile("\\p{So}+");

    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "\\*{2,}|_{2,}|`{1,3}|~~");

    public ResponseGuard() {
        this.blocklist = new LinkedHashSet<>();
        blocklist.add("queryFlightsBetweenTwoCities");
        blocklist.add("queryFlightBookingDetails");
        blocklist.add("modifyFlightBooking");
        blocklist.add("cancelFlightBooking");
        blocklist.add("getWeatherByCity");
        blocklist.add("FlightsBetweenTwoCities");
        blocklist.add("FlightBookingDetails");
        blocklist.add("BookingDetails");
        blocklist.add("WeatherByCity");

        blocklist.add("MyBatisSystemException");
        blocklist.add("SystemException");
        blocklist.add("DataAccessException");
        blocklist.add("NullPointerException");
        blocklist.add("IllegalArgumentException");
        blocklist.add("RuntimeException");
        blocklist.add("SQLException");
        blocklist.add("IOException");
        blocklist.add("Exception");

        blocklist.add("fromCity");
        blocklist.add("toCity");
        blocklist.add("bookingNumber");
        blocklist.add("严格输入");
        blocklist.add("官方航班查询");
        blocklist.add("实时航班信息");
        blocklist.add("调取");
        blocklist.add("重新查询");
    }

    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        for (String term : blocklist) {
            result = result.replace(term, "");
        }

        result = result.replace("调用", "查询");
        result = result.replace("工具", "");
        result = result.replace("函数", "");
        result = result.replace("[]", "");

        result = result.replace("正在查询", "查询");
        result = result.replace("实时查询", "查询");
        result = result.replace("为您查询", "查询");
        result = result.replace("调取", "查询");
        result = result.replace("请稍候", "");

        result = result.replace("空结果", "没有结果");
        result = result.replace("空列表", "没有结果");
        result = result.replace("返回空", "没有找到");

        result = result.replace("系统返回了", "我们");
        result = result.replace("系统返回", "我们");
        result = result.replace("系统查询", "我们");
        result = result.replace("系统无任何报错", "我们确认");
        result = result.replace("系统", "");

        result = result.replace("在查询过程中遇到了", "");
        result = result.replace("查询过程中遇到了", "");
        result = result.replace("临时异常", "暂时无法查询");
        result = result.replace("异常", "");

        result = result.replace("我刚刚为您重新查询", "查询结果");
        result = result.replace("我马上为您", "为您");
        result = result.replace("我刚刚为您", "为您");
        result = result.replace("我问", "您问");
        result = result.replace("我建议", "建议");
        result = result.replace("我全程在线", "");
        result = result.replace("真实、高效、愉快地陪您启程", "");

        result = result.replace("我理解您想", "您想");
        result = result.replace("我理解您", "");
        result = result.replace("这表示", "");
        result = result.replace("需要我帮您", "需要帮您");
        result = result.replace("您还有其他需求", "您还有其他需要");
        result = result.replace("严格输入", "");
        result = result.replace("清晰提供", "提供");
        result = result.replace("我为您", "为您");

        result = EMOJI_ANY_PATTERN.matcher(result).replaceAll("");
        result = MARKDOWN_PATTERN.matcher(result).replaceAll("");

        result = result.replace("高铁", "");
        result = result.replace("动车", "");
        result = result.replace("火车", "");
        result = result.replace("列车", "");
        result = result.replace("城际", "");
        result = result.replace("普速", "");
        result = result.replace("特快", "");
        result = result.replace("铁路", "");
        result = result.replace("火车站", "");
        result = result.replace("12306", "");
        result = result.replace("二等座", "");
        result = result.replace("一等座", "");
        result = result.replace("商务座", "");
        result = result.replace("无座", "");
        result = result.replace("扫码进站", "");

        result = SYSTEM_PROMPT_LEAK_PATTERN.matcher(result).replaceAll("");

        result = result.replaceAll("\\s{2,}", " ");

        result = result.replaceAll("(?<![0-9])\\.(?!\\s*[0-9])", "。");
        result = result.replaceAll("\\s*,\\s*", "，");

        return result.trim();
    }

    public boolean containsPromptLeak(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return PROMPT_LEAK_DETECT_PATTERN.matcher(text).find();
    }

    public ValidationResult validate(String fullResponse) {
        if (fullResponse == null || fullResponse.isEmpty()) {
            return ValidationResult.isValid();
        }

        StringBuilder violations = new StringBuilder();

        if (PHONE_PATTERN.matcher(fullResponse).find()) {
            violations.append("[电话号码]");
        }
        if (TRAIN_PATTERN.matcher(fullResponse).find()) {
            violations.append("[火车/高铁信息]");
        }
        if (TRAIN_STATION_PATTERN.matcher(fullResponse).find()) {
            violations.append("[火车站/铁路信息]");
        }
        if (COMPETITOR_AIRLINE_PATTERN.matcher(fullResponse).find()) {
            violations.append("[竞争航司名称]");
        }
        if (HALLUCINATED_PRICE_PATTERN.matcher(fullResponse).find()) {
            violations.append("[虚构价格描述]");
        }
        if (HALLUCINATED_SCHEDULE_PATTERN.matcher(fullResponse).find()) {
            violations.append("[虚构班次/时长]");
        }
        if (FAKE_COOPERATION_PATTERN.matcher(fullResponse).find()) {
            violations.append("[虚构合作关系]");
        }

        if (violations.length() > 0) {
            String violationDetails = violations.toString();
            logger.warn("Response validation failed. Violations: {}", violationDetails);
            return ValidationResult.invalid(violationDetails);
        }

        return ValidationResult.isValid();
    }

    public record ValidationResult(boolean valid, String violations) {

        public boolean checkValid() {
                return valid;
            }

            public static ValidationResult isValid() {
                return new ValidationResult(true, "");
            }

            public static ValidationResult invalid(String violations) {
                return new ValidationResult(false, violations);
            }
        }
}