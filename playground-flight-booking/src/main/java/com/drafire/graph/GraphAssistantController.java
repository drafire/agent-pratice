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
                    if ("generate_response".equals(nodeOutput.node())) {
                        Object reply = nodeOutput.state().value("reply", "");
                        if (reply != null && !reply.toString().isEmpty()) {
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