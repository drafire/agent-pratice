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

### 4.1 添加 Graph 依赖

``xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-graph</artifactId>
</dependency>
``

### 4.2 定义工作流状态

新建 src/main/java/ai/spring/demo/ai/playground/graph/AgentState.java：

``java
package ai.spring.demo.ai.playground.graph;

import java.util.Map;

public class AgentState {
    private String userInput;
    private String intent;
    private String reply;
    private String bookingNumber;
    private String customerName;
    private Map<String, Object> params;

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getBookingNumber() { return bookingNumber; }
    public void setBookingNumber(String bookingNumber) { this.bookingNumber = bookingNumber; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
``

### 4.3 创建意图识别节点

新建 src/main/java/ai/spring/demo/ai/playground/graph/node/IntentClassifierNode.java：

``java
package ai.spring.demo.ai.playground.graph.node;

import ai.spring.demo.ai.playground.graph.AgentState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.util.function.Function;

public class IntentClassifierNode implements Function<AgentState, AgentState> {

    private final ChatClient chatClient;

    public IntentClassifierNode(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public AgentState apply(AgentState state) {
        String intent = chatClient.prompt()
            .user("""
                判断用户意图，只返回以下类别之一：
                BOOKING_DETAILS / CHANGE_BOOKING / CANCEL_BOOKING
                / SEARCH_FLIGHTS / WEATHER / GENERAL
                用户输入: {input}
                """)
            .build().call().content();
        state.setIntent(intent.trim());
        return state;
    }
}
``

### 4.4 构建 Graph 主配置

新建 src/main/java/ai/spring/demo/ai/playground/graph/FlightAgentGraph.java：

``java
package ai.spring.demo.ai.playground.graph;

import ai.spring.demo.ai.playground.graph.node.IntentClassifierNode;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
public class FlightAgentGraph {

    private final ChatModel chatModel;

    public FlightAgentGraph(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Bean
    public CompiledGraph flightAgentCompiledGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategy = new KeyStrategyFactoryBuilder()
            .addPatternStrategy("userInput", (o1, o2) -> o2)
            .addPatternStrategy("intent", (o1, o2) -> o2)
            .addPatternStrategy("reply", (o1, o2) -> o2)
            .build();

        var intentNode = new IntentClassifierNode(chatModel);

        StateGraph graph = new StateGraph(keyStrategy)
            .addNode("classify_intent", node_async(intentNode))
            .addEdge(START, "classify_intent")
            .addEdge("classify_intent", END);

        return graph.compile(CompiledGraph.builder().saver(new MemorySaver()).build());
    }
}
``

### 4.5 创建 Graph 控制器

新建 src/main/java/ai/spring/demo/ai/playground/graph/GraphChatController.java：

``java
package ai.spring.demo.ai.playground.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphChatController {

    private final CompiledGraph compiledGraph;

    public GraphChatController(CompiledGraph compiledGraph) {
        this.compiledGraph = compiledGraph;
    }

    @PostMapping("/chat")
    public Flux<String> chat(@RequestParam String chatId, @RequestParam String userMessage) {
        return compiledGraph.stream(
            Map.of("userInput", userMessage),
            RunnableConfig.builder().threadId(chatId).build()
        );
    }
}
``

### 4.6 工作流对比

``
【改造前】直线调用
  用户输入 → ChatClient → 工具调用 → 回复

【改造后】Graph 工作流
  用户输入 → 意图识别(LLM分类) → 条件路由 → 各分支处理 → 回复
``

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
