# 企业级 AI 项目工程化路线图

> 基于 playground-flight-booking 项目，从当前状态逐步升级为可面试展示的企业级 AI Agent 应用。

---

## 当前状态

```
✅ 输入/输出治理（ChatGlobalAspect + ResponseGuard）
✅ 双架构对比（Function Calling + StateGraph）
✅ AOP 统一拦截（ChatGlobalAspect）
✅ 流式响应 SSE
✅ 人机交互确认（取消订单二次确认）
✅ RAG 知识库（PGVector）
✅ 对话记忆（JDBC Chat Memory）
✅ JWT 认证
⚠️ 结构化日志                           ← 建议优先补
```

---

## 第一层：基础工程化

### 1.1 结构化日志 + traceId

**目标**：每个请求携带 `traceId`，全链路可追踪，日志输出为 JSON 格式方便 ELK 采集。

**实现方案**：
- `logback-spring.xml` 配置 JSON 格式输出
- MDC 注入 `traceId`、`chatId`、`mode`
- 在 `ChatGlobalAspect` 入口处设置 MDC，出口处清理

**改动量**：新增 `logback-spring.xml` + 修改 `ChatGlobalAspect` 约 10 行

**面试话术**：
> "所有日志输出为 JSON 格式，每条日志携带 `traceId`、`chatId`、`mode` 三个维度标签。通过 ELK 可以按任意维度聚合查询，比如快速定位某个用户的所有对话链路，或者统计 Graph 模式的错误率。"

---

### 1.2 功能开关（Feature Flags）

**目标**：通过配置动态切换 Graph / Function Calling 模式，不需要重新部署。

**实现方案**：
```yaml
app:
  feature:
    graph-mode: true          # 是否启用 Graph 模式
    weather-enabled: true     # 是否启用天气查询
    input-guard-enabled: true # 是否启用输入治理
```

**改动量**：新增 `FeatureFlags` 配置类 + 修改 `ChatGlobalAspect` 约 5 行

---

## 第二层：可观测性（面试最高频考点）

### 2.1 Metrics 指标埋点

**目标**：Prometheus + Grafana 可视化监控，面试官一眼看到"这人懂运维"。

**指标清单**：

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `chat_requests_total` | Counter | mode, intent, status | 请求总量 |
| `chat_request_duration_seconds` | Histogram | mode, intent | 请求耗时分布 |
| `chat_llm_call_duration_seconds` | Histogram | node, model | LLM 调用耗时 |
| `chat_token_usage_total` | Counter | mode, type(input/output) | Token 消耗 |
| `chat_input_guarded_total` | Counter | reason | 输入拦截次数 |
| `chat_errors_total` | Counter | mode, error_type | 错误次数 |

**实现方案**：
- 引入 `micrometer-registry-prometheus` 依赖
- 在 `ChatGlobalAspect` 中埋点 Counter + Timer
- 暴露 `/actuator/prometheus` 端点
- 可选：`docker-compose.yml` 加入 Prometheus + Grafana

**改动量**：新增依赖 + 修改 `ChatGlobalAspect` 约 30 行 + 可选 `docker-compose.yml`

**面试话术**：
> "我用 Micrometer 做了全链路指标埋点，覆盖请求量、耗时分布、Token 消耗、安全拦截率、错误率五个维度。所有指标通过 `/actuator/prometheus` 暴露，接入 Grafana 后可以做实时大盘，比如按意图类型看 P99 耗时、按小时看 Token 消耗趋势。"

---

### 2.2 链路追踪（Tracing）

**目标**：一次请求经过 4 个 Graph 节点，每个节点的耗时一目了然。

**实现方案**：
- Micrometer Observation API（Spring Boot 3 原生支持）
- 在每个 Graph 节点中创建 Observation
- 可选：对接 Zipkin 或 Jaeger 做可视化

**改动量**：修改 4 个 Node 约 20 行 + 可选依赖

---

## 第三层：弹性与容错

### 3.1 熔断（Circuit Breaker）

**目标**：LLM API 连续失败时自动熔断，快速失败而非拖垮调用方。

**实现方案**（Resilience4j）：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm-call:
        sliding-window-size: 10        # 滑动窗口大小
        failure-rate-threshold: 50     # 失败率阈值 50%
        wait-duration-in-open-state: 30s  # 熔断后 30 秒尝试半开
        permitted-number-of-calls-in-half-open-state: 3
```

**改动量**：新增依赖 + 新增配置 + 修改 `ChatGlobalAspect` 约 10 行

**面试话术**：
> "LLM 是外部依赖，不可控。我用 Resilience4j 做了熔断保护：滑动窗口 10 次调用中失败率超过 50% 就熔断，30 秒后半开探测。熔断期间返回友好提示而非让用户无限等待。"

---

### 3.2 限流（Rate Limiter）

**目标**：防止恶意刷接口，控制 LLM 调用成本。

**实现方案**：
```yaml
resilience4j:
  ratelimiter:
    instances:
      chat-api:
        limit-for-period: 20          # 每秒最多 20 个请求
        limit-refresh-period: 1s
        timeout-duration: 500ms        # 等待 500ms 获取不到令牌就拒绝
```

**改动量**：新增配置 + 修改 `ChatGlobalAspect` 约 5 行

---

### 3.3 重试（Retry）

**目标**：网络抖动导致 LLM 调用失败时自动重试。

**实现方案**：
```yaml
resilience4j:
  retry:
    instances:
      llm-retry:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2   # 1s → 2s → 4s
        retry-exceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
```

**改动量**：新增配置 + 修改 `ChatGlobalAspect` 约 5 行

---

### 3.4 超时（Time Limiter）

**目标**：LLM 调用设置 30 秒超时，防止资源泄漏。

**改动量**：修改 `ChatClient` 构建时设置 `timeout` 参数

---

### 3.5 舱壁隔离（Bulkhead）

**目标**：不同类型请求使用独立线程池，防止互相影响。

**示例**：搜索航班和取消订单使用不同线程池，避免大量搜索请求阻塞取消操作。

**改动量**：新增配置 + 约 10 行代码

---

## 第四层：多 Agent 协同

### 4.1 架构设计

```
                         ┌──────────────────┐
                         │  SupervisorAgent  │
                         │  （总控 Agent）     │
                         │  意图识别 + 路由    │
                         └───┬────┬────┬─────┘
                             │    │    │
              ┌──────────────┘    │    └──────────────┐
              ▼                   ▼                   ▼
     ┌────────────────┐  ┌──────────────┐  ┌────────────────┐
     │ FlightExpert   │  │WeatherExpert │  │ ComplaintAgent │
     │ （机票专家）     │  │ （天气专家）   │  │ （投诉处理）     │
     │                 │  │              │  │                 │
     │ 搜索/预订/改签   │  │ 查天气/建议   │  │ 投诉/退款/升级   │
     └────────────────┘  └──────────────┘  └────────────────┘
```

### 4.2 实现方案

- 利用 `FlightAgentGraphConfig` 的 `addConditionalEdges` 做路由分发
- 每个子 Agent 是一个独立的 `StateGraph` 子图
- `SupervisorAgent` 根据意图分类结果路由到对应子 Agent
- 子 Agent 之间可以互相调用（如天气专家建议改签 → 调用机票专家）

**改动量**：新增 3 个 `SubGraphConfig` + 修改 `FlightAgentGraphConfig` 约 100 行

---

## 第五层：配置与运维

### 5.1 动态 Prompt 管理

**目标**：System Prompt 存储在数据库，支持热更新。

**方案**：
- 建表 `prompt_template`（id, name, version, content, status）
- 启动时加载到本地缓存（Caffeine），定时刷新
- 提供管理接口 `/api/admin/prompts` 做 CRUD

**改动量**：新增表 + 新增 `PromptTemplateService` + 管理接口

---

### 5.2 Docker Compose 一键启动

**目标**：面试官一行命令就能跑起来。

```yaml
services:
  app:
    build: .
    ports: [9000:9000]
    depends_on: [mysql, postgres]
  mysql:
    image: mysql:8
  postgres:
    image: pgvector/pgvector:pg16
```

**改动量**：新增 `Dockerfile` + `docker-compose.yml`

---

### 5.3 健康检查

**目标**：K8s 就绪探针 + 存活探针。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

**改动量**：新增配置约 10 行

---

### 5.4 优雅停机

**目标**：收到 SIGTERM 后等待现有请求处理完毕再退出。

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

**改动量**：新增配置 4 行

---

## 第六层：测试与评测

### 6.1 传统测试

**改动量**：

| 测试类型 | 技术 | 说明 |
|---------|------|------|
| 单元测试 | JUnit + Mockito | Service 层、工具类 |
| 集成测试 | Testcontainers | 启动真实 MySQL/PG 容器 |
| API 测试 | WebTestClient | 测试 SSE 流式接口 |

---

### 6.2 AI 专项评测（面试亮点）

**Prompt 评测集**：准备 100 条测试用例，覆盖 6 种意图 + 边界情况，人工标注预期结果。

**意图分类准确率**：
```
             预测
           搜索 查询 改签 取消 天气 闲聊
实际 搜索   45   1    0    0    0    0    准确率 97.8%
     查询    0  18    1    0    0    0    准确率 94.7%
     改签    0   1   14    0    0    0    准确率 93.3%
     取消    0   0    0   10    0    0    准确率 100%
     天气    0   0    0    0    8    0    准确率 100%
     闲聊    0   0    0    0    0    3    准确率 100%
```

**安全对抗测试**：用 10 类注入攻击用例测试输入治理，统计拦截率。

---

## 实施优先级

| 优先级 | 功能 | 预计耗时 | 面试价值 |
|:---:|------|:---:|:---:|
| **P0** | 结构化日志 + traceId | 5 分钟 | ⭐⭐⭐⭐⭐ |
| **P0** | Micrometer 指标埋点 | 15 分钟 | ⭐⭐⭐⭐⭐ |
| **P0** | 功能开关 | 10 分钟 | ⭐⭐⭐ |
| **P1** | 熔断 + 限流 + 重试 | 30 分钟 | ⭐⭐⭐⭐⭐ |
| **P1** | 健康检查 + 优雅停机 | 10 分钟 | ⭐⭐⭐⭐ |
| **P1** | Docker Compose | 20 分钟 | ⭐⭐⭐⭐ |
| **P2** | 多 Agent 协同 | 1-2 小时 | ⭐⭐⭐⭐⭐ |
| **P2** | 链路追踪 | 15 分钟 | ⭐⭐⭐ |
| **P2** | 动态 Prompt 管理 | 30 分钟 | ⭐⭐⭐ |
| **P3** | AI 评测集 | 1 小时 | ⭐⭐⭐⭐⭐ |
| **P3** | 单元/集成测试 | 1 小时 | ⭐⭐⭐⭐ |

---

## 面试一分钟陈述模板

> "这个项目是我从零搭建的企业级 AI Agent 应用。架构上我做了 **6 层工程化**：  
> **安全治理层**用 AOP 切面做了输入 10 类注入检测和输出净化，**可观测性层**用 Micrometer 埋点了 5 个维度的 Prometheus 指标，**弹性容错层**用 Resilience4j 做了熔断、限流、重试和舱壁隔离，**Agent 协同层**用 StateGraph 实现了 Supervisor 总控 + 3 个专家子 Agent 的协作架构，**运维层**做了 Docker Compose 一键启动、健康检查和优雅停机，**评测层**准备了 100 条 Prompt 评测集并统计了意图分类混淆矩阵。  
> 您现在可以 `docker-compose up` 一键启动，访问 `localhost:9000/graph.html` 体验 Graph Agent，或者 `localhost:3000` 看 Grafana 监控大盘。"