# 智能机票助手 —— 循序渐进扩展指南

> 基于 playground-flight-booking 项目，手把手带你从简单到复杂逐步升级。

---

## 目录

- [准备工作：理解现有项目结构](#0-准备工作理解现有项目结构)
- [第一步：添加天气查询工具（青铜级）](#1-第一步添加天气查询工具青铜级)
- [第二步：添加航班实时搜索工具（白银级）](#2-第二步添加航班实时搜索工具白银级)
- [第三步：引入 MCP 动态工具（黄金级）](#3-第三步引入-mcp-动态工具黄金级)
- [第四步：升级为 Graph 工作流（钻石级）](#4-第四步升级为-graph-工作流钻石级)
- [第五步：集成 DashScope 知识库替代内存向量库（王者级）](#5-第五步集成-dashscope-知识库替代内存向量库王者级)

---

## 0. 准备工作：理解现有项目结构

### 0.1 项目目录树

``
playground-flight-booking/
├── src/main/java/ai/spring/demo/ai/playground/
│   ├── AgentApplication.java          ← 启动类
│   ├── data/
│   │   ├── Booking.java               ← 订单实体
│   │   ├── BookingData.java           ← 内存数据存储
│   │   ├── BookingStatus.java         ← 枚举
│   │   ├── BookingClass.java          ← 枚举
│   │   └── Customer.java              ← 客户实体
│   ├── services/
│   │   ├── CustomerSupportAssistant.java  ← 核心 AI 助手
│   │   ├── FlightBookingService.java      ← 模拟航班预订
│   │   └── BookingTools.java              ← 工具函数注册
│   └── client/
│       ├── AssistantController.java   ← SSE 流式聊天 API
│       └── BookingController.java     ← 订单查询 + 页面入口
├── src/main/resources/
│   ├── application.properties
│   ├── rag/terms-of-service.txt
│   └── templates/index.html
├── frontend/
└── pom.xml
``

### 0.2 核心调用链路

``
用户输入
  → AssistantController.chat()
    → CustomerSupportAssistant.chat()
      ├─ PromptChatMemoryAdvisor（多轮记忆）
      ├─ QuestionAnswerAdvisor（RAG检索）
      ├─ 模型判断 → 调用工具
      └─ 生成自然语言回复 → SSE流式返回
``

### 0.3 关键代码速览

**CustomerSupportAssistant.java** - 助手构建方式：

``java
this.chatClient = modelBuilder
    .defaultSystem("""
        您是"Funnair"航空公司的客户聊天支持代理...
    """)
    .defaultAdvisors(
        PromptChatMemoryAdvisor.builder(chatMemory).build(),
        QuestionAnswerAdvisor.builder(vectorStore).build(),
        new SimpleLoggerAdvisor()
    )
    .defaultToolNames("getBookingDetails", "changeBooking", "cancelBooking")
    .build();
``

**BookingTools.java** - 工具注册：

``java
@Configuration
public class BookingTools {
    @Bean
    @Description("获取机票预定详细信息")
    public Function<BookingDetailsRequest, BookingDetails> getBookingDetails() {
        return request -> flightBookingService.getBookingDetails(...);
    }
}
``

---

## 1. 第一步：添加天气查询工具（青铜级）

> **目标**：让助手能回答"北京明天天气怎么样？"  
> **技术**：RestClient 调用外部 API + @Bean Function 注册  
> **难度**：⭐

### 1.1 创建天气数据模型

新建 src/main/java/ai/spring/demo/ai/playground/data/WeatherInfo.java：

``java
package ai.spring.demo.ai.playground.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record WeatherInfo(
    String city,
    String date,
    String temperature,
    String weather,
    String humidity,
    String windDirection,
    String windPower
) {}
``

### 1.2 创建天气服务

新建 src/main/java/ai/spring/demo/ai/playground/services/WeatherService.java：

``java
package ai.spring.demo.ai.playground.services;

import ai.spring.demo.ai.playground.data.WeatherInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WeatherService {

    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);
    private final RestClient restClient;

    public WeatherService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://restapi.amap.com")
            .build();
    }

    public WeatherInfo getWeather(String city) {
        logger.info("查询天气: {}", city);
        return new WeatherInfo(city, "2026-06-27",
            "26°C~32°C", "晴转多云", "65%", "东南风", "3-4级");
    }
}
``

### 1.3 创建天气工具

新建 src/main/java/ai/spring/demo/ai/playground/services/WeatherTools.java：

``java
package ai.spring.demo.ai.playground.services;

import ai.spring.demo.ai.playground.data.WeatherInfo;
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

    public record WeatherRequest(String city) {}

    @Bean
    @Description("查询指定城市的天气情况")
    public Function<WeatherRequest, WeatherInfo> queryWeather() {
        return request -> {
            logger.info("工具调用: queryWeather, city={}", request.city());
            return weatherService.getWeather(request.city());
        };
    }
}
``

### 1.4 注册新工具

修改 CustomerSupportAssistant.java：

``java
.defaultToolNames(
    "getBookingDetails", "changeBooking", "cancelBooking",
    "queryWeather"       // ← 新增
)
``

---

## 2. 第二步：添加航班实时搜索工具（白银级）

> **目标**：让助手能搜索航班（如"北京到上海明天有哪些航班？"）  
> **难度**：⭐⭐

### 2.1 扩展 FlightBookingService

在 FlightBookingService.java 末尾添加：

``java
import java.util.Random;

public List<FlightInfo> searchFlights(String from, String to, String date) {
    List<FlightInfo> flights = new ArrayList<>();
    Random random = new Random();
    String[] airlines = {"CA", "MU", "CZ", "HU", "3U", "ZH"};
    String[] times = {"06:30", "08:00", "10:30", "13:00", "15:30", "18:00", "20:30"};
    int count = 3 + random.nextInt(3);
    for (int i = 0; i < count; i++) {
        String flightNo = airlines[random.nextInt(airlines.length)] + (1000 + random.nextInt(9000));
        String time = times[random.nextInt(times.length)];
        int price = 500 + random.nextInt(2000);
        flights.add(new FlightInfo(flightNo, from, to, date, time, price, random.nextInt(50) + 10));
    }
    return flights;
}

public record FlightInfo(
    String flightNo, String from, String to, String date,
    String departureTime, int price, int availableSeats
) {}
``

### 2.2 创建航班搜索工具

新建 src/main/java/ai/spring/demo/ai/playground/services/FlightSearchTools.java：

``java
package ai.spring.demo.ai.playground.services;

import ai.spring.demo.ai.playground.services.FlightBookingService.FlightInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;

@Configuration
public class FlightSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(FlightSearchTools.class);
    private final FlightBookingService flightBookingService;

    public FlightSearchTools(FlightBookingService flightBookingService) {
        this.flightBookingService = flightBookingService;
    }

    public record FlightSearchRequest(String from, String to, String date) {}

    @Bean
    @Description("搜索指定城市之间的可用航班")
    public Function<FlightSearchRequest, List<FlightInfo>> searchFlights() {
        return request -> {
            logger.info("searchFlights: from={}, to={}, date={}",
                request.from(), request.to(), request.date());
            return flightBookingService.searchFlights(request.from(), request.to(), request.date());
        };
    }
}
``

### 2.3 注册新工具

``java
.defaultToolNames(
    "getBookingDetails", "changeBooking", "cancelBooking",
    "queryWeather",     // 第一步
    "searchFlights"     // ← 第二步
)
``

### 2.4 更新 System Prompt

``java
.defaultSystem("""
    您是"Funnair"航空公司的客户聊天支持代理...
    您的核心能力包括：
    - 机票预订详情查询、日期改签、预订取消
    - 航班搜索：根据出发城市、到达城市和日期搜索可用航班
    - 天气查询：查询指定城市的天气情况
    请讲中文。今天的日期是 {current_date}.
""")
``

---

## 3. 第三步：引入 MCP 动态工具（黄金级）

> **目标**：将工具从编译期硬编码升级为 MCP 协议动态注册，支持热插拔  
> **难度**：⭐⭐⭐

### 3.1 MCP 架构

``
┌──────────────────────────────────┐
│  机票助手 (Java)                   │
│  ChatClient.defaultTools(mcpTools)│
│  McpSyncClient (SSE Transport)    │
└──────────┬───────────────────────┘
           │ HTTP SSE
┌──────────▼───────────────────────┐
│  MCP Server (Python/Go/Node.js)  │
│  · weather_query                 │
│  · flight_search                 │
│  · hotel_recommend               │
└──────────────────────────────────┘
``

### 3.2 添加 MCP 依赖

``xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-client-webflux</artifactId>
</dependency>
``

### 3.3 编写 MCP Server（Python）

新建 mcp-server/weather_server.py：

``python
from mcp.server import Server
from mcp.server.sse import SseServerTransport

server = Server("weather-mcp-server")

@server.tool()
async def query_weather(city: str) -> str:
    data = {
        "北京": "晴转多云 26°C~32°C",
        "上海": "多云 28°C~34°C",
        "广州": "雷阵雨 30°C~36°C",
    }
    return data.get(city, f"{city}: 晴 20°C~28°C")

if __name__ == "__main__":
    import uvicorn
    from starlette.applications import Starlette
    from starlette.routing import Route
    sse = SseServerTransport("/messages")
    async def handle_sse(request):
        async with sse.connect_sse(request.scope, request.receive, request._send) as streams:
            await server.run(streams[0], streams[1], server.create_initialization_options())
    app = Starlette(routes=[
        Route("/sse", endpoint=handle_sse),
        Route("/messages", endpoint=sse.handle_post_message, methods=["POST"]),
    ])
    uvicorn.run(app, host="0.0.0.0", port=8000)
``

### 3.4 Java 端配置 MCP Client

在 AgentApplication.java 添加：

``java
import org.springframework.ai.mcp.client.McpSyncClient;
import org.springframework.ai.mcp.client.transport.HttpClientSseClientTransport;

@Bean
public McpSyncClient weatherMcpClient() {
    return McpSyncClient.builder()
        .transport(new HttpClientSseClientTransport("http://localhost:8000/sse"))
        .build();
}
``

### 3.5 修改 CustomerSupportAssistant

``java
public CustomerSupportAssistant(
    ChatClient.Builder modelBuilder,
    VectorStore vectorStore,
    ChatMemory chatMemory,
    McpSyncClient weatherMcpClient    // ← 注入 MCP Client
) {
    this.chatClient = modelBuilder
        .defaultSystem("...")
        .defaultAdvisors(...)
        .defaultToolNames("getBookingDetails", "changeBooking", "cancelBooking")
        .defaultTools(weatherMcpClient.getTools())  // ← 动态注入
        .build();
}
``

### 3.6 MCP 优势

| 对比 | 传统 @Bean | MCP |
|------|-----------|-----|
| 注册 | 编译期硬编码 | 运行时动态发现 |
| 新增工具 | 改代码+重启 | 启动MCP Server即可 |
| 跨语言 | 仅Java | Python/Go/Node.js |
| 热插拔 | ❌ | ✅ |

---

## 4. 第四步：升级为 Graph 工作流（钻石级）

> **目标**：将直线 ChatClient 调用升级为 StateGraph 多节点工作流  
> **难度**：⭐⭐⭐⭐  
> **适用场景**：需要多步串联、条件路由、HITL（人工确认）时才有必要。简单场景下 ChatClient 更高效。

### 4.0 改造前评估：要不要上 Graph？

| 当前项目特点 | 判断 |
|-------------|:---:|
| 3 个简单 CRUD 工具，模型自己选就行 | ❌ 不需要 Graph |
| "先查天气 → 再搜航班 → 推荐订票" 多步串联 | ✅ 需要 Graph |
| 取消订单需要**用户二次确认**（HITL） | ✅ 需要 Graph |
| 多个工具需要**并行执行** | ✅ 需要 Graph |
| 需要做**条件分支**（VIP 用户走不同流程） | ✅ 需要 Graph |

> **结论**：当前项目是直线型调用（用户输入 → 模型判断 → 调一个工具 → 返回），ChatClient 完全够用。
> 但如果后续要加入 HITL 确认、多步串联等场景，Graph 是更好的选择。以下方案设计了完整的 Graph 架构，
> 覆盖了这些场景，你可以按需裁剪。

### 4.1 完整工作流设计

```
                     ┌──────────────────────────────────────┐
                     │           用户输入                    │
                     └──────────────┬───────────────────────┘
                                    │
                                    ▼
                     ┌──────────────────────────────┐
                     │    classify_intent            │
                     │    (LLM 意图分类)              │
                     │    输出: BOOKING_DETAILS       │
                     │         CHANGE_BOOKING         │
                     │         CANCEL_BOOKING         │
                     │         SEARCH_FLIGHTS         │
                     │         WEATHER                │
                     │         GENERAL                │
                     └──────────────┬───────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
          BOOKING_DETAILS    CHANGE/CANCEL    SEARCH/WEATHER
                    │               │               │
                    ▼               ▼               ▼
          ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
          │ extract_params│ │ extract_params│ │ extract_params│
          │ (提取预定号、  │ │ (提取预定号、  │ │ (提取城市、    │
          │  姓名)        │ │  姓名、新日期) │ │  日期)        │
          └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
                 │                │                │
                 ▼                ▼                ▼
          ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
          │ query_booking │ │ change/       │ │ search/      │
          │ (执行查询)     │ │ cancel_booking│ │ weather      │
          │               │ │ (执行操作)     │ │ (执行查询)    │
          └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
                 │                │                │
                 └────────────────┼────────────────┘
                                  │
                                  ▼
                       ┌──────────────────┐
                       │ generate_response │
                       │ (LLM 生成自然语言) │
                       └────────┬─────────┘
                                │
                                ▼
                              END
```

**关键设计决策：**

| 决策 | 选择 | 理由 |
|------|------|------|
| 意图分类 | 单独 LLM 调用 | 比正则匹配更准确，能处理模糊表达 |
| 参数提取 | 单独 LLM 调用 | 将口语化输入转为结构化参数，避免工具调用失败 |
| 响应生成 | 统一节点 | 所有分支最终汇聚到一个节点，避免重复代码 |
| HITL | 取消操作前暂停 | 取消不可恢复，需要用户二次确认 |

### 4.2 添加 Graph 依赖

``xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-graph-core</artifactId>
    <version>${spring-ai-alibaba.version}</version>
</dependency>
``

### 4.3 定义工作流状态

新建 `src/main/java/com/drafire/graph/FlightAgentState.java`：

``java
package com.drafire.graph;

import java.time.LocalDate;

/**
 * Graph 工作流全局状态。
 * 每个节点读取/写入此对象，实现节点间数据传递。
 */
public class FlightAgentState {

    // ========== 输入 ==========
    private String userInput;          // 用户原始输入

    // ========== 意图识别 ==========
    private String intent;             // 识别出的意图

    // ========== 提取的参数 ==========
    private String bookingNumber;      // 预定编号
    private String customerName;       // 乘客姓名
    private String from;               // 出发地
    private String to;                 // 目的地
    private LocalDate date;            // 日期
    private String bookingClass;       // 舱位
    private String weatherCity;        // 天气查询城市
    private LocalDate weatherDate;     // 天气查询日期

    // ========== 工具执行结果 ==========
    private String toolResult;         // 工具返回的原始结果

    // ========== 输出 ==========
    private String reply;              // 最终回复

    // ========== HITL ==========
    private boolean cancelConfirmed;   // 取消是否已确认

    // ========== getters/setters ==========
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getBookingClass() { return bookingClass; }
    public void setBookingClass(String bookingClass) { this.bookingClass = bookingClass; }

    public String getWeatherCity() { return weatherCity; }
    public void setWeatherCity(String weatherCity) { this.weatherCity = weatherCity; }

    public LocalDate getWeatherDate() { return weatherDate; }
    public void setWeatherDate(LocalDate weatherDate) { this.weatherDate = weatherDate; }

    public String getToolResult() { return toolResult; }
    public void setToolResult(String toolResult) { this.toolResult = toolResult; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public boolean isCancelConfirmed() { return cancelConfirmed; }
    public void setCancelConfirmed(boolean cancelConfirmed) { this.cancelConfirmed = cancelConfirmed; }
}
``

### 4.4 意图分类节点

新建 `src/main/java/com/drafire/graph/node/IntentClassifierNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Function;

/**
 * 意图分类节点：用 LLM 将用户输入分类为预定义的意图类别。
 * 这是 Graph 的入口节点，所有用户输入先经过这里。
 */
public class IntentClassifierNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(IntentClassifierNode.class);

    private final ChatClient chatClient;

    public IntentClassifierNode(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[意图分类] 用户输入: {}", state.getUserInput());

        String intent = chatClient.prompt()
                .user("""
                    你是一个意图分类器。根据用户输入，只返回以下类别之一（不要返回其他内容）：

                    - BOOKING_DETAILS: 查询订单详情
                    - CHANGE_BOOKING: 修改/改签订单
                    - CANCEL_BOOKING: 取消订单
                    - SEARCH_FLIGHTS: 搜索航班
                    - WEATHER: 查询天气
                    - GENERAL: 一般对话/闲聊

                    用户输入: {input}

                    意图:""")
                .param("input", state.getUserInput())
                .call()
                .content();

        intent = intent.trim().toUpperCase();
        state.setIntent(intent);
        logger.info("[意图分类] 识别结果: {}", intent);
        return state;
    }
}
``

### 4.5 参数提取节点

新建 `src/main/java/com/drafire/graph/node/ParameterExtractorNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * 参数提取节点：根据意图类型，从用户口语化输入中提取结构化参数。
 * 这一步将"帮我查一下张三的订单" → { bookingNumber: "xxx", customerName: "张三" }
 */
public class ParameterExtractorNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(ParameterExtractorNode.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatClient chatClient;

    public ParameterExtractorNode(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        String intent = state.getIntent();
        logger.info("[参数提取] 意图: {}, 输入: {}", intent, state.getUserInput());

        String schema = switch (intent) {
            case "BOOKING_DETAILS", "CANCEL_BOOKING" -> """
                    {
                      "bookingNumber": "预定编号（如 BKG-001）",
                      "customerName": "乘客姓名"
                    }""";
            case "CHANGE_BOOKING" -> """
                    {
                      "bookingNumber": "预定编号",
                      "customerName": "乘客姓名",
                      "from": "新出发地（如 北京）",
                      "to": "新目的地（如 上海）",
                      "date": "新日期（格式 yyyy-MM-dd）"
                    }""";
            case "SEARCH_FLIGHTS" -> """
                    {
                      "from": "出发城市",
                      "to": "目的城市",
                      "date": "出发日期（格式 yyyy-MM-dd）"
                    }""";
            case "WEATHER" -> """
                    {
                      "city": "城市名称",
                      "date": "日期（格式 yyyy-MM-dd，今天用当前日期）"
                    }""";
            default -> null;
        };

        if (schema == null) {
            return state; // GENERAL 不需要提取参数
        }

        try {
            String json = chatClient.prompt()
                    .user("""
                        从用户输入中提取结构化参数。只返回 JSON，不要返回其他内容。

                        目标 JSON 格式:
                        {schema}

                        用户输入: {input}

                        JSON:""")
                    .param("schema", schema)
                    .param("input", state.getUserInput())
                    .call()
                    .content();

            json = extractJson(json);
            JsonNode root = objectMapper.readTree(json);

            if (root.has("bookingNumber")) state.setBookingNumber(root.get("bookingNumber").asText());
            if (root.has("customerName")) state.setCustomerName(root.get("customerName").asText());
            if (root.has("from")) state.setFrom(root.get("from").asText());
            if (root.has("to")) state.setTo(root.get("to").asText());
            if (root.has("date")) state.setDate(LocalDate.parse(root.get("date").asText()));
            if (root.has("city")) state.setWeatherCity(root.get("city").asText());

            logger.info("[参数提取] 结果: bookingNumber={}, customerName={}, from={}, to={}, date={}",
                    state.getBookingNumber(), state.getCustomerName(),
                    state.getFrom(), state.getTo(), state.getDate());
        } catch (JsonProcessingException e) {
            logger.error("[参数提取] JSON 解析失败: {}", e.getMessage());
        }

        return state;
    }

    private String extractJson(String raw) {
        int start = raw.indexOf("{");
        int end = raw.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
``

### 4.6 业务节点（查询 / 改签 / 取消 / 搜索 / 天气）

新建 `src/main/java/com/drafire/graph/node/QueryBookingNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.drafire.serivce.FlightBookingService;
import com.drafire.interceptor.ResponseRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 查询订单节点：调用 FlightBookingService 获取订单详情。
 */
public class QueryBookingNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(QueryBookingNode.class);

    private final FlightBookingService flightBookingService;
    private final ResponseRenderer responseRenderer;

    public QueryBookingNode(FlightBookingService flightBookingService, ResponseRenderer responseRenderer) {
        this.flightBookingService = flightBookingService;
        this.responseRenderer = responseRenderer;
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[查询订单] bookingNumber={}, customerName={}",
                state.getBookingNumber(), state.getCustomerName());

        var booking = flightBookingService.getBooking(
                state.getBookingNumber(), state.getCustomerName());
        state.setToolResult(responseRenderer.renderBooking(booking));
        return state;
    }
}
``

新建 `src/main/java/com/drafire/graph/node/ChangeBookingNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.drafire.serivce.FlightBookingService;
import com.drafire.interceptor.ResponseRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 改签节点：调用 FlightBookingService 修改订单。
 */
public class ChangeBookingNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(ChangeBookingNode.class);

    private final FlightBookingService flightBookingService;
    private final ResponseRenderer responseRenderer;

    public ChangeBookingNode(FlightBookingService flightBookingService, ResponseRenderer responseRenderer) {
        this.flightBookingService = flightBookingService;
        this.responseRenderer = responseRenderer;
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[改签] bookingNumber={}, from={}, to={}, date={}",
                state.getBookingNumber(), state.getFrom(), state.getTo(), state.getDate());

        flightBookingService.changeBooking(
                state.getBookingNumber(), state.getCustomerName(),
                state.getDate(), state.getFrom(), state.getTo());
        state.setToolResult(responseRenderer.renderModifySuccess(
                state.getBookingNumber(), state.getFrom(), state.getTo(), state.getDate()));
        return state;
    }
}
``

新建 `src/main/java/com/drafire/graph/node/CancelBookingNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.drafire.serivce.FlightBookingService;
import com.drafire.interceptor.ResponseRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 取消订单节点：先查询订单信息让用户确认，确认后才执行取消。
 * 这是 HITL（Human-in-the-Loop）的典型场景。
 */
public class CancelBookingNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(CancelBookingNode.class);

    private final FlightBookingService flightBookingService;
    private final ResponseRenderer responseRenderer;

    public CancelBookingNode(FlightBookingService flightBookingService, ResponseRenderer responseRenderer) {
        this.flightBookingService = flightBookingService;
        this.responseRenderer = responseRenderer;
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        if (!state.isCancelConfirmed()) {
            // HITL: 先查询订单详情，让用户确认
            logger.info("[取消订单-HITL] 查询订单详情供用户确认: bookingNumber={}",
                    state.getBookingNumber());
            var booking = flightBookingService.getBooking(
                    state.getBookingNumber(), state.getCustomerName());
            String detail = responseRenderer.renderBooking(booking);
            state.setToolResult("""
                    ⚠️ 请确认要取消以下订单（取消后不可恢复）：

                    %s

                    请回复"确认取消"来执行取消操作，或回复"不取消"来放弃。""".formatted(detail));
            state.setCancelConfirmed(true); // 标记为已请求确认
        } else {
            // 用户已确认，执行取消
            logger.info("[取消订单-执行] 用户已确认，执行取消: bookingNumber={}",
                    state.getBookingNumber());
            flightBookingService.cancelBooking(
                    state.getBookingNumber(), state.getCustomerName());
            state.setToolResult(responseRenderer.renderCancelSuccess(state.getBookingNumber()));
        }
        return state;
    }
}
``

新建 `src/main/java/com/drafire/graph/node/SearchFlightsNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.drafire.serivce.FlightSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 搜索航班节点：调用 FlightSearchService 搜索航班。
 */
public class SearchFlightsNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(SearchFlightsNode.class);

    private final FlightSearchService flightSearchService;

    public SearchFlightsNode(FlightSearchService flightSearchService) {
        this.flightSearchService = flightSearchService;
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[搜索航班] from={}, to={}, date={}",
                state.getFrom(), state.getTo(), state.getDate());

        String result = flightSearchService.searchFlights(
                state.getFrom(), state.getTo(), state.getDate());
        state.setToolResult(result);
        return state;
    }
}
``

新建 `src/main/java/com/drafire/graph/node/QueryWeatherNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import com.drafire.serivce.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 查询天气节点：调用 WeatherService 查询天气。
 */
public class QueryWeatherNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(QueryWeatherNode.class);

    private final WeatherService weatherService;

    public QueryWeatherNode(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[查询天气] city={}, date={}",
                state.getWeatherCity(), state.getWeatherDate());

        String result = weatherService.getWeather(
                state.getWeatherCity(), state.getWeatherDate());
        state.setToolResult(result);
        return state;
    }
}
``

### 4.7 响应生成节点

新建 `src/main/java/com/drafire/graph/node/ResponseGeneratorNode.java`：

``java
package com.drafire.graph.node;

import com.drafire.graph.FlightAgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Function;

/**
 * 响应生成节点：将工具执行结果转化为自然语言回复。
 * 所有分支最终汇聚到这个节点。
 */
public class ResponseGeneratorNode implements Function<FlightAgentState, FlightAgentState> {

    private static final Logger logger = LoggerFactory.getLogger(ResponseGeneratorNode.class);

    private final ChatClient chatClient;

    public ResponseGeneratorNode(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                    你是 Funnair 航空公司的客服助手。请根据工具执行结果，
                    用友好、专业的中文回复用户。如果是取消订单的确认请求，
                    引导用户明确回复"确认取消"或"不取消"。""")
                .build();
    }

    @Override
    public FlightAgentState apply(FlightAgentState state) {
        logger.info("[响应生成] 意图: {}, 工具结果: {}",
                state.getIntent(),
                state.getToolResult() != null ? state.getToolResult().substring(0, Math.min(100, state.getToolResult().length())) : "null");

        String reply;
        if ("GENERAL".equals(state.getIntent())) {
            // 一般对话，不需要工具结果
            reply = chatClient.prompt()
                    .user(state.getUserInput())
                    .call()
                    .content();
        } else {
            reply = chatClient.prompt()
                    .user("""
                        用户原始输入: {input}
                        工具执行结果: {result}

                        请生成自然语言回复:""")
                    .param("input", state.getUserInput())
                    .param("result", state.getToolResult())
                    .call()
                    .content();
        }

        state.setReply(reply);
        logger.info("[响应生成] 回复: {}", reply.substring(0, Math.min(100, reply.length())));
        return state;
    }
}
``

### 4.8 条件路由

新建 `src/main/java/com/drafire/graph/FlightAgentRouter.java`：

``java
package com.drafire.graph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Function;

/**
 * 条件路由器：根据意图将请求路由到不同的业务节点。
 * 被 StateGraph.addConditionalEdges() 调用。
 */
public class FlightAgentRouter implements Function<FlightAgentState, String> {

    private static final Logger logger = LoggerFactory.getLogger(FlightAgentRouter.class);

    // 意图 → 下一个节点名称
    private static final Map<String, String> ROUTE_MAP = Map.of(
            "BOOKING_DETAILS", "query_booking",
            "CHANGE_BOOKING", "change_booking",
            "CANCEL_BOOKING", "cancel_booking",
            "SEARCH_FLIGHTS", "search_flights",
            "WEATHER", "query_weather",
            "GENERAL", "generate_response"
    );

    // 需要先提取参数的意图
    private static final Map<String, String> PARAM_ROUTE_MAP = Map.of(
            "BOOKING_DETAILS", "extract_params",
            "CHANGE_BOOKING", "extract_params",
            "CANCEL_BOOKING", "extract_params",
            "SEARCH_FLIGHTS", "extract_params",
            "WEATHER", "extract_params"
    );

    /**
     * 第一阶段路由：意图分类后 → 参数提取 或 直接生成回复
     */
    public static final Function<FlightAgentState, String> AFTER_INTENT = state -> {
        String intent = state.getIntent();
        String next = PARAM_ROUTE_MAP.getOrDefault(intent, "generate_response");
        logger.info("[路由-意图后] intent={} → {}", intent, next);
        return next;
    };

    /**
     * 第二阶段路由：参数提取后 → 具体业务节点
     */
    public static final Function<FlightAgentState, String> AFTER_PARAMS = state -> {
        String intent = state.getIntent();
        String next = ROUTE_MAP.getOrDefault(intent, "generate_response");
        logger.info("[路由-参数后] intent={} → {}", intent, next);
        return next;
    };
}
``

### 4.9 组装 Graph 主配置

新建 `src/main/java/com/drafire/graph/FlightAgentGraphConfig.java`：

``java
package com.drafire.graph;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.drafire.graph.node.*;
import com.drafire.serivce.FlightBookingService;
import com.drafire.serivce.FlightSearchService;
import com.drafire.serivce.WeatherService;
import com.drafire.interceptor.ResponseRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class FlightAgentGraphConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlightAgentGraphConfig.class);

    private final ChatClient.Builder chatClientBuilder;
    private final FlightBookingService flightBookingService;
    private final FlightSearchService flightSearchService;
    private final WeatherService weatherService;
    private final ResponseRenderer responseRenderer;

    public FlightAgentGraphConfig(ChatClient.Builder chatClientBuilder,
                                  FlightBookingService flightBookingService,
                                  FlightSearchService flightSearchService,
                                  WeatherService weatherService,
                                  ResponseRenderer responseRenderer) {
        this.chatClientBuilder = chatClientBuilder;
        this.flightBookingService = flightBookingService;
        this.flightSearchService = flightSearchService;
        this.weatherService = weatherService;
        this.responseRenderer = responseRenderer;
    }

    @Bean
    public StateGraph flightAgentStateGraph() throws GraphStateException {
        // 1. Key 策略：每个节点更新时覆盖前一个值
        KeyStrategyFactory keyStrategy = new KeyStrategyFactoryBuilder()
                .addPatternStrategy("userInput", new ReplaceStrategy())
                .addPatternStrategy("intent", new ReplaceStrategy())
                .addPatternStrategy("bookingNumber", new ReplaceStrategy())
                .addPatternStrategy("customerName", new ReplaceStrategy())
                .addPatternStrategy("from", new ReplaceStrategy())
                .addPatternStrategy("to", new ReplaceStrategy())
                .addPatternStrategy("date", new ReplaceStrategy())
                .addPatternStrategy("weatherCity", new ReplaceStrategy())
                .addPatternStrategy("weatherDate", new ReplaceStrategy())
                .addPatternStrategy("toolResult", new ReplaceStrategy())
                .addPatternStrategy("reply", new ReplaceStrategy())
                .addPatternStrategy("cancelConfirmed", new ReplaceStrategy())
                .build();

        // 2. 创建节点
        var intentNode = new IntentClassifierNode(chatClientBuilder);
        var paramNode = new ParameterExtractorNode(chatClientBuilder);
        var queryBookingNode = new QueryBookingNode(flightBookingService, responseRenderer);
        var changeBookingNode = new ChangeBookingNode(flightBookingService, responseRenderer);
        var cancelBookingNode = new CancelBookingNode(flightBookingService, responseRenderer);
        var searchFlightsNode = new SearchFlightsNode(flightSearchService);
        var queryWeatherNode = new QueryWeatherNode(weatherService);
        var responseNode = new ResponseGeneratorNode(chatClientBuilder);

        // 3. 构建 Graph
        StateGraph graph = new StateGraph(keyStrategy)
                // 注册所有节点
                .addNode("classify_intent", node_async(intentNode))
                .addNode("extract_params", node_async(paramNode))
                .addNode("query_booking", node_async(queryBookingNode))
                .addNode("change_booking", node_async(changeBookingNode))
                .addNode("cancel_booking", node_async(cancelBookingNode))
                .addNode("search_flights", node_async(searchFlightsNode))
                .addNode("query_weather", node_async(queryWeatherNode))
                .addNode("generate_response", node_async(responseNode))

                // 边: START → 意图分类
                .addEdge(START, "classify_intent")

                // 条件边: 意图分类 → 参数提取 或 直接回复
                .addConditionalEdges("classify_intent", FlightAgentRouter.AFTER_INTENT,
                        Map.of(
                                "extract_params", "extract_params",
                                "generate_response", "generate_response"
                        ))

                // 条件边: 参数提取 → 各业务节点
                .addConditionalEdges("extract_params", FlightAgentRouter.AFTER_PARAMS,
                        Map.of(
                                "query_booking", "query_booking",
                                "change_booking", "change_booking",
                                "cancel_booking", "cancel_booking",
                                "search_flights", "search_flights",
                                "query_weather", "query_weather"
                        ))

                // 边: 各业务节点 → 响应生成
                .addEdge("query_booking", "generate_response")
                .addEdge("change_booking", "generate_response")
                .addEdge("cancel_booking", "generate_response")
                .addEdge("search_flights", "generate_response")
                .addEdge("query_weather", "generate_response")

                // 边: 响应生成 → END
                .addEdge("generate_response", END);

        // 4. 打印 PlantUML 可视化
        GraphRepresentation representation = graph.getGraph(
                GraphRepresentation.Type.PLANTUML, "Flight Agent Graph");
        logger.info("\n=== Flight Agent 工作流 ===\n{}\n========================",
                representation.content());

        return graph;
    }
}
``

### 4.10 创建 Graph 控制器

新建 `src/main/java/com/drafire/graph/GraphAssistantController.java`：

``java
package com.drafire.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * 暴露 SSE 接口，前端通过 EventSource 连接。
 * 与原有 AssistantController 并存，方便对比测试。
 */
@RestController
@RequestMapping("/api/graph")
public class GraphAssistantController {

    private static final Logger logger = LoggerFactory.getLogger(GraphAssistantController.class);

    private final CompiledGraph compiledGraph;

    public GraphAssistantController(StateGraph flightAgentStateGraph) throws GraphStateException {
        this.compiledGraph = flightAgentStateGraph.compile();
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestParam String chatId,
            @RequestParam String userMessage) {

        Map<String, Object> input = Map.of("userInput", userMessage);
        RunnableConfig config = RunnableConfig.builder().threadId(chatId).build();

        Flux<NodeOutput> stream = compiledGraph.stream(input, config);

        Sinks.Many<ServerSentEvent<String>> sink = Sinks.many().unicast().onBackpressureBuffer();

        stream.subscribe(
                nodeOutput -> {
                    // 提取回复内容
                    if (nodeOutput != null && nodeOutput.state() != null) {
                        Object reply = nodeOutput.state().get("reply");
                        if (reply != null) {
                            sink.tryEmitNext(ServerSentEvent.builder(reply.toString()).build());
                        }
                    }
                },
                error -> {
                    logger.error("Graph 执行失败", error);
                    sink.tryEmitNext(ServerSentEvent.builder("抱歉，处理请求时出错了").build());
                    sink.tryEmitComplete();
                },
                () -> sink.tryEmitComplete()
        );

        return sink.asFlux()
                .doOnCancel(() -> logger.info("客户端断开连接"))
                .doOnError(e -> logger.error("SSE 流错误", e));
    }
}
``

### 4.11 工作流对比

``
【改造前】直线调用
  用户输入 → ChatClient → 模型自主判断 → 工具调用 → 回复
  优点: 简单、延迟低、一次 LLM 调用
  缺点: 不可控、不可观测、无法做 HITL

【改造后】Graph 工作流
  用户输入 → 意图分类(LLM) → 参数提取(LLM) → 业务节点(Java) → 响应生成(LLM)
  优点: 可控、可观测、支持 HITL、每个节点可独立测试
  缺点: 3 次 LLM 调用，延迟更高、成本更高
``

### 4.12 前端适配

在 `index.html` 中新增 Graph 模式的切换按钮：

``javascript
// 发送消息时判断模式
function sendMessage() {
    const input = document.getElementById('userInput');
    const message = input.value.trim();
    if (!message) return;

    const isGraphMode = document.getElementById('graphModeToggle').checked;
    const url = isGraphMode
        ? `/api/graph/chat?chatId=${chatId}&userMessage=${encodeURIComponent(message)}`
        : `/api/assistant/chat?chatId=${chatId}&userMessage=${encodeURIComponent(message)}`;

    // ... EventSource 逻辑
}
``

``html
<!-- 模式切换开关 -->
<label style="margin-left: 10px; cursor: pointer;">
    <input type="checkbox" id="graphModeToggle" />
    <span style="font-size: 12px; color: #888;">Graph 模式</span>
</label>
``

### 4.13 运行验证

``
# 启动后访问 http://localhost:8080
# 勾选 "Graph 模式" 开关，测试以下场景:

1. "帮我查一下张三的订单 BKG-001"        → 意图: BOOKING_DETAILS → 查询 → 回复
2. "把张三的 BKG-001 改签到下周五北京到上海" → 意图: CHANGE_BOOKING → 改签 → 回复
3. "我要取消张三的 BKG-001"                → 意图: CANCEL_BOOKING → HITL确认 → 取消
4. "上海到深圳明天有哪些航班"              → 意图: SEARCH_FLIGHTS → 搜索 → 回复
5. "北京明天天气怎么样"                   → 意图: WEATHER → 查询 → 回复
6. "你好，你是谁"                         → 意图: GENERAL → 直接回复

# 查看日志确认节点流转:
grep "\[意图分类\]" logs/app.log
grep "\[路由-" logs/app.log
``

### 4.14 何时选择 Graph？

| 场景 | 推荐方案 |
|------|---------|
| 简单 CRUD 工具，模型自主调用 | ChatClient + Tools（保持现有） |
| 需要多步串联（先查天气再搜航班） | Graph |
| 需要 HITL 确认（取消前二次确认） | Graph |
| 需要条件分支（VIP 不同流程） | Graph |
| 需要并行执行多个工具 | Graph |
| 需要节点级别的可观测性 | Graph |

> **总结**：Graph 不是银弹。当前项目推荐保持 ChatClient + 原有 Controller，
> Graph 作为可选的高级模式，通过前端的模式开关让用户自由切换。

---

## 5. 第五步：集成 DashScope 知识库替代内存向量库（王者级）

> **目标**：将 SimpleVectorStore（内存）替换为 DashScope 云端知识库  
> **难度**：⭐⭐

### 5.1 升级对比

| 对比 | SimpleVectorStore（当前） | DashScope 知识库（升级后） |
|------|--------------------------|---------------------------|
| 存储 | 内存 | 云端持久化 |
| 容量 | 受内存限制 | 几乎无限 |
| 重启 | 数据丢失 | 数据持久 |
| 检索 | 基础余弦相似度 | 阿里云专业检索算法 |

### 5.2 修改 VectorStore Bean

修改 AgentApplication.java：

``java
// 修改前
@Bean
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
}

// 修改后
@Bean
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    return DashScopeVectorStore.builder(embeddingModel)
        .options(DashScopeStoreOptions.builder()
            .withIndexName("funnair_knowledge_base")
            .build())
        .build();
}
``

### 5.3 配置

``properties
spring.ai.dashscope.index-name=funnair_knowledge_base
``

---

## 完整升级路线总览

``
当前能力                    升级后能力
─────────────────────────────────────────────────
3 个工具 ────────────────→ 8+ 个工具
硬编码工具注册 ──────────→ MCP 动态工具注册（热插拔）
直线 ChatClient ─────────→ Graph 多节点工作流
无意图识别 ──────────────→ LLM 意图分类 + 条件分支
内存向量库 ──────────────→ DashScope 云端知识库
``

---

## 快速验证

``
第一步: "北京今天天气怎么样？"
第二步: "上海到深圳明天有哪些航班"
第三步: "用 MCP 工具查成都的天气"（需先启动 MCP Server）
第四步: "我要改签我的订单"（走 Graph 意图识别 → 改签分支）
第五步: "取消订单有什么规定？"（检索 DashScope 知识库）
``

---

> **提示**：每一步都是渐进式的，按顺序完成，每完成一步就运行验证，确保不破坏已有功能后再进行下一步。